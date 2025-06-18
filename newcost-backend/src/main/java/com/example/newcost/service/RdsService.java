package com.example.newcost.service;

import com.example.newcost.model.RdsRecommendationDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.pricing.PricingClient;
import software.amazon.awssdk.services.pricing.model.Filter;
import software.amazon.awssdk.services.pricing.model.GetProductsRequest;
import software.amazon.awssdk.services.pricing.model.GetProductsResponse;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DBInstance;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RdsService {

    private static final Logger logger = LoggerFactory.getLogger(RdsService.class);

    private final RdsClient rdsClient;
    private final CloudWatchClient cloudWatchClient;
    private final PricingClient pricingClient;

    @Autowired
    public RdsService(RdsClient rdsClient, CloudWatchClient cloudWatchClient, PricingClient pricingClient) {
        this.rdsClient = rdsClient;
        this.cloudWatchClient = cloudWatchClient;
        this.pricingClient = pricingClient;
    }

    public List<RdsRecommendationDTO> getRdsRecommendations() {
        List<DBInstance> dbInstances = listRdsInstances();
        return dbInstances.stream()
                .map(this::buildRdsRecommendation)
                .collect(Collectors.toList());
    }

    private List<DBInstance> listRdsInstances() {
        try {
            DescribeDbInstancesResponse response = rdsClient.describeDBInstances();
            return response.dbInstances();
        } catch (Exception e) {
            logger.error("Error fetching RDS instances: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private RdsRecommendationDTO buildRdsRecommendation(DBInstance instance) {
        RdsRecommendationDTO dto = new RdsRecommendationDTO();
        dto.setDbInstanceIdentifier(instance.dbInstanceIdentifier());
        dto.setDbClusterIdentifier(instance.dbClusterIdentifier() != null ? instance.dbClusterIdentifier() : "N/A");
        dto.setEngine(instance.engine());
        dto.setCurrentInstanceTypes(Collections.singletonList(instance.dbInstanceClass()));
        dto.setCurrentStorageType(instance.storageType());

        // Fetch utilization metrics
        String utilization = getCpuUtilization(instance.dbInstanceIdentifier());
        dto.setUtilization(utilization);

        // Fetch pricing from the aws
        String currentPrice = getRdsOnDemandPrice(instance.dbInstanceClass(), instance.engine(), instance.availabilityZone());
        dto.setCurrentOndemandPrice(currentPrice);

        // Generate recommendations
        generateRecommendations(dto, instance, Double.parseDouble(currentPrice), utilization);

        return dto;
    }

    private String getCpuUtilization(String dbInstanceIdentifier) {
        try {
            Instant now = Instant.now();
            Instant startTime = now.minus(7, ChronoUnit.DAYS); // Last 7 days for utilization

            GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
                    .namespace("AWS/RDS")
                    .metricName("CPUUtilization")
                    .dimensions(Dimension.builder()
                            .name("DBInstanceIdentifier")
                            .value(dbInstanceIdentifier)
                            .build())
                    .startTime(startTime)
                    .endTime(now)
                    .period(86400) // Daily average
                    .statistics(Statistic.AVERAGE)
                    .build();

            GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);
            if (!response.datapoints().isEmpty()) {
                double avgCpu = response.datapoints().stream()
                        .mapToDouble(Datapoint::average)
                        .average()
                        .orElse(0.0);
                return String.format("%.2f%%", avgCpu);
            }
        } catch (Exception e) {
            logger.error("Error fetching CPU utilization for {}: {}", dbInstanceIdentifier, e.getMessage(), e);
        }
        return "N/A";
    }

    private String getRdsOnDemandPrice(String instanceClass, String engine, String region) {
        try {
            String pricingRegion = getPricingRegion(region);

            GetProductsRequest request = GetProductsRequest.builder()
                    .serviceCode("AmazonRDS")
                    .filters(
                            Filter.builder().type("TERM_MATCH").field("instanceType").value(instanceClass).build(),
                            Filter.builder().type("TERM_MATCH").field("databaseEngine").value(normalizeEngine(engine)).build(),
                            Filter.builder().type("TERM_MATCH").field("location").value(pricingRegion).build(),
                            Filter.builder().type("TERM_MATCH").field("deploymentOption").value("Single-AZ").build()
                    )
                    .formatVersion("aws_v1")
                    .build();

            GetProductsResponse response = pricingClient.getProducts(request);
            if (!response.priceList().isEmpty()) {
                String priceJson = response.priceList().get(0).toString();
                return parsePriceFromJson(priceJson);
            }
        } catch (Exception e) {
            logger.error("Error fetching price for RDS {} in {}: {}", instanceClass, region, e.getMessage(), e);
        }
        return "0.00";
    }

    private String normalizeEngine(String engine) {
        // Map RDS engine names to Pricing API format
        return switch (engine.toLowerCase()) {
            case "mysql" -> "MYSQL";
            case "postgres" -> "PostgreSQL";
            case "sqlserver" -> "SQL Server";
            case "oracle" -> "Oracle";
            default -> engine;
        };
    }

    private String getPricingRegion(String region) {
        // Map AWS regions to Pricing API regions
        if (region.startsWith("us-east")) return "US East (N. Virginia)";
        if (region.startsWith("us-west-1")) return "US West (N. California)";
        if (region.startsWith("us-west-2")) return "US West (Oregon)";
        if (region.startsWith("eu-west")) return "EU (Ireland)";
        return region;
    }

    private String parsePriceFromJson(String priceJson) {
        try {
            JsonNode rootNode = new ObjectMapper().readTree(priceJson);
            JsonNode terms = rootNode.path("terms").path("OnDemand");
            Iterator<Map.Entry<String, JsonNode>> termIter = terms.fields();

            while (termIter.hasNext()) {
                Map.Entry<String, JsonNode> term = termIter.next();
                JsonNode priceDimensions = term.getValue().path("priceDimensions");
                Iterator<Map.Entry<String, JsonNode>> dimensionIter = priceDimensions.fields();

                while (dimensionIter.hasNext()) {
                    Map.Entry<String, JsonNode> dimension = dimensionIter.next();
                    String pricePerUnit = dimension.getValue().path("pricePerUnit").path("USD").asText();
                    if (!pricePerUnit.isEmpty()) {
                        return String.format("%.4f", Double.parseDouble(pricePerUnit));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing price JSON: {}", e.getMessage(), e);
        }
        return "0.00";
    }

    private void generateRecommendations(RdsRecommendationDTO dto, DBInstance instance, double currentPrice, String utilization) {
        List<String> recommendedInstanceTypes = new ArrayList<>();
        String finding = "No optimization needed";
        String recommendedPrice = "0.00";
        double utilizationValue = parseUtilization(utilization);

        // Example recommendation logic based on CPU utilization
        if (utilizationValue < 20.0) {
            finding = "Underutilized instance";
            recommendedInstanceTypes = getSmallerInstanceTypes(instance.dbInstanceClass());
            if (!recommendedInstanceTypes.isEmpty()) {
                String recommendedType = recommendedInstanceTypes.get(0);
                recommendedPrice = getRdsOnDemandPrice(recommendedType, instance.engine(), instance.availabilityZone());
                dto.setRecommendedOndemandPrice(recommendedPrice);
                dto.setRecommendedInstanceTypes(recommendedInstanceTypes);
            }
        } else if (utilizationValue > 80.0) {
            finding = "Overutilized instance";
            recommendedInstanceTypes = getLargerInstanceTypes(instance.dbInstanceClass());
            if (!recommendedInstanceTypes.isEmpty()) {
                String recommendedType = recommendedInstanceTypes.get(0);
                recommendedPrice = getRdsOnDemandPrice(recommendedType, instance.engine(), instance.availabilityZone());
                dto.setRecommendedOndemandPrice(recommendedPrice);
                dto.setRecommendedInstanceTypes(recommendedInstanceTypes);
            }
        }

        // Storage type recommendation
        if ("gp2".equals(instance.storageType())) {
            finding = finding.equals("No optimization needed") ? "Storage optimization available" : finding + "; Storage optimization available";
            dto.setRecommendedPrice(String.format("%.2f", currentPrice * 0.8)); // Assume gp3 is ~20% cheaper
        }

        dto.setFinding(finding);
        dto.setRecommendedPrice(recommendedPrice);
    }

    private double parseUtilization(String utilization) {
        try {
            return Double.parseDouble(utilization.replace("%", ""));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private List<String> getSmallerInstanceTypes(String currentType) {
        // Simplified logic for demonstration; adjust based on actual instance families
        Map<String, String> smallerTypes = new HashMap<>();
        smallerTypes.put("db.m5.large", "db.m5.medium");
        smallerTypes.put("db.m5.medium", "db.m5.small");
        smallerTypes.put("db.t3.large", "db.t3.medium");
        smallerTypes.put("db.t3.medium", "db.t3.small");

        return smallerTypes.containsKey(currentType) ? Collections.singletonList(smallerTypes.get(currentType)) : Collections.emptyList();
    }

    private List<String> getLargerInstanceTypes(String currentType) {
        // Simplified logic for demonstration; adjust based on actual instance families
        Map<String, String> largerTypes = new HashMap<>();
        largerTypes.put("db.m5.medium", "db.m5.large");
        largerTypes.put("db.m5.small", "db.m5.medium");
        largerTypes.put("db.t3.medium", "db.t3.large");
        largerTypes.put("db.t3.small", "db.t3.medium");

        return largerTypes.containsKey(currentType) ? Collections.singletonList(largerTypes.get(currentType)) : Collections.emptyList();
    }
}