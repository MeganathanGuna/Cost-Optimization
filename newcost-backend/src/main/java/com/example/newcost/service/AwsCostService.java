package com.example.newcost.service;

import com.amazonaws.services.costexplorer.AWSCostExplorer;
import com.amazonaws.services.costexplorer.model.*;
import com.example.newcost.model.CostDataDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AwsCostService {
    private final AWSCostExplorer costExplorer;
    private static final Logger logger = LoggerFactory.getLogger(AwsCostService.class);

    private static final Set<String> EC2_SERVICES = Set.of(
            "Amazon Elastic Compute Cloud",
            "EC2 - Other",
            "Amazon EC2 Container Registry",
            "Amazon EC2 Container Service",
            "Amazon EC2 Systems Manager"
    );

    @Autowired
    public AwsCostService(AWSCostExplorer costExplorer) {
        this.costExplorer = costExplorer;
    }

    public CostDataDTO getCostData(int month, int year) {
        CostDataDTO costData = new CostDataDTO();

        try {
            // Use UTC for date calculations to match AWS console
            YearMonth yearMonth = YearMonth.of(year, month);
            LocalDate firstDayOfMonth = yearMonth.atDay(1);
            LocalDate lastDayOfMonth = yearMonth.atEndOfMonth().plusDays(1);;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            // 1. Get total cost using AmortizedCost to match AWS UI
            GetCostAndUsageRequest totalRequest = new GetCostAndUsageRequest()
                    .withTimePeriod(new DateInterval()
                            .withStart(firstDayOfMonth.format(formatter))
                            .withEnd(lastDayOfMonth.format(formatter)))
                    .withGranularity("MONTHLY")
                    .withMetrics("AmortizedCost"); // Changed to match AWS UI

            GetCostAndUsageResult totalResponse = costExplorer.getCostAndUsage(totalRequest);
            if (!totalResponse.getResultsByTime().isEmpty()) {
                ResultByTime result = totalResponse.getResultsByTime().get(0);
                if (result.getTotal() != null && result.getTotal().get("AmortizedCost") != null) {
                    costData.setEstimatedGrandTotal(formatAmount(result.getTotal().get("AmortizedCost").getAmount()));
                }
            }

            // 2. Get costs grouped by service with AmortizedCost
            GetCostAndUsageRequest serviceRequest = new GetCostAndUsageRequest()
                    .withTimePeriod(new DateInterval()
                            .withStart(firstDayOfMonth.format(formatter))
                            .withEnd(lastDayOfMonth.format(formatter)))
                    .withGranularity("MONTHLY")
                    .withMetrics("AmortizedCost") // Changed to match AWS UI
                    .withGroupBy(new GroupDefinition()
                            .withType("DIMENSION")
                            .withKey("SERVICE"));

            GetCostAndUsageResult serviceResponse = costExplorer.getCostAndUsage(serviceRequest);
            if (!serviceResponse.getResultsByTime().isEmpty()) {
                ResultByTime result = serviceResponse.getResultsByTime().get(0);

                // Group all EC2-related costs exactly like AWS UI
                Map<String, Double> serviceCosts = result.getGroups().stream()
                        .collect(Collectors.groupingBy(
                                g -> normalizeServiceName(g.getKeys().get(0)),
                                Collectors.summingDouble(g -> Double.parseDouble(g.getMetrics().get("AmortizedCost").getAmount()))
                        ));

                Map.Entry<String, Double> maxEntry = serviceCosts.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .orElse(null);

                if (maxEntry != null) {
                    costData.setHighestServiceSpend(formatAmount(String.valueOf(maxEntry.getValue())));
                    costData.setHighestServiceName(maxEntry.getKey());
                }
            }

            // 3. Get region costs with AmortizedCost
            GetCostAndUsageRequest regionRequest = new GetCostAndUsageRequest()
                    .withTimePeriod(new DateInterval()
                            .withStart(firstDayOfMonth.format(formatter))
                            .withEnd(lastDayOfMonth.format(formatter)))
                    .withGranularity("MONTHLY")
                    .withMetrics("AmortizedCost") // Changed to match AWS UI
                    .withGroupBy(new GroupDefinition()
                            .withType("DIMENSION")
                            .withKey("REGION"));

            GetCostAndUsageResult regionResponse = costExplorer.getCostAndUsage(regionRequest);
            if (!regionResponse.getResultsByTime().isEmpty()) {
                ResultByTime result = regionResponse.getResultsByTime().get(0);
                result.getGroups().stream()
                        .max(Comparator.comparingDouble(g ->
                                Double.parseDouble(g.getMetrics().get("AmortizedCost").getAmount())))
                        .ifPresent(g -> {
                            costData.setHighestRegionSpend(formatAmount(g.getMetrics().get("AmortizedCost").getAmount()));
                            costData.setHighestRegionName(
                                    g.getKeys().get(0).equals("ap-south-1") ?
                                            "AP South (Mumbai)" : g.getKeys().get(0));
                        });
            }

        } catch (Exception e) {
            logger.error("Error fetching cost data for {}-{}: {}", month, year, e.getMessage(), e);
        }

        return costData;
    }

    private String normalizeServiceName(String awsServiceName) {
        // Map AWS service names exactly to what the console shows
        if (awsServiceName.contains("Elastic Compute Cloud") ||
                awsServiceName.contains("EC2 - Other") ||
                awsServiceName.contains("EC2 Container")) {
            return "Elastic Compute Cloud";
        }
        return awsServiceName.replace("Amazon ", "");
    }


    private boolean isEC2Service(String serviceName) {
        return EC2_SERVICES.stream().anyMatch(serviceName::contains);
    }

    private String formatAmount(String amount) {
        try {
            double value = Double.parseDouble(amount);
            return String.format("%.2f", value);
        } catch (NumberFormatException e) {
            logger.warn("Failed to format amount: {}", amount, e);
            return amount;
        }
    }
}