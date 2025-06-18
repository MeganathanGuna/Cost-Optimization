package com.example.newcost.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.computeoptimizer.ComputeOptimizerClient;
import software.amazon.awssdk.services.computeoptimizer.model.*;
import software.amazon.awssdk.services.pricing.PricingClient;
import software.amazon.awssdk.services.pricing.model.GetProductsRequest;
import software.amazon.awssdk.services.pricing.model.GetProductsResponse;
import software.amazon.awssdk.services.pricing.model.FilterType;
import software.amazon.awssdk.services.pricing.model.Filter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class ComputeOptimizerService {

    private final ComputeOptimizerClient computeOptimizerClient;
    private final PricingClient pricingClient;

    @Autowired
    public ComputeOptimizerService(ComputeOptimizerClient computeOptimizerClient,
                                   PricingClient pricingClient) {
        this.computeOptimizerClient = computeOptimizerClient;
        this.pricingClient = pricingClient;
    }

    public List<InstanceRecommendation> getEc2InstanceRecommendations() {
        List<InstanceRecommendation> allRecommendations = new ArrayList<>();

        GetEc2InstanceRecommendationsRequest request = GetEc2InstanceRecommendationsRequest.builder()
                .recommendationPreferences(RecommendationPreferences.builder()
                        .cpuVendorArchitectures(CpuVendorArchitecture.AWS_ARM64, CpuVendorArchitecture.CURRENT)
                        .build())
                .build();

        try {
            GetEc2InstanceRecommendationsResponse response = computeOptimizerClient.getEC2InstanceRecommendations(request);
            allRecommendations.addAll(response.instanceRecommendations());
        } catch (ComputeOptimizerException e) {
            System.err.println("Error fetching recommendations: " + e.awsErrorDetails().errorMessage());
            e.printStackTrace();
        }

        return allRecommendations;
    }

    public double getOnDemandPrice(String instanceType, String region) {
        try {
            String pricingRegion = getPricingRegion(region);

            GetProductsRequest request = GetProductsRequest.builder()
                    .serviceCode("AmazonEC2")
                    .filters(
                            Filter.builder().type("TERM_MATCH").field("instanceType").value(instanceType).build(),
                            Filter.builder().type("TERM_MATCH").field("location").value(pricingRegion).build(),
                            Filter.builder().type("TERM_MATCH").field("operatingSystem").value("Linux").build(),
                            Filter.builder().type("TERM_MATCH").field("tenancy").value("Shared").build(),
                            Filter.builder().type("TERM_MATCH").field("preInstalledSw").value("NA").build(),
                            Filter.builder().type("TERM_MATCH").field("capacitystatus").value("Used").build()
                    )
                    .formatVersion("aws_v1")
                    .build();

            GetProductsResponse response = pricingClient.getProducts(request);

            if (!response.priceList().isEmpty()) {
                String priceJson = response.priceList().get(0).toString();
                return parsePriceFromJson(priceJson);
            }
        } catch (Exception e) {
            System.err.println("Error fetching price for " + instanceType + " in " + region + ": " + e.getMessage());
        }
        return 0.0; // Return 0 if price cannot be determined
    }

    private String getPricingRegion(String region) {
        // Map AWS regions to pricing API regions
        if (region.startsWith("us-east")) return "US East (N. Virginia)";
        if (region.startsWith("us-west-1")) return "US West (N. California)";
        if (region.startsWith("us-west-2")) return "US West (Oregon)";
        if (region.startsWith("eu-west")) return "EU (Ireland)";
        return region;
    }

    private double parsePriceFromJson(String priceJson) {
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
                        return Double.parseDouble(pricePerUnit);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing price JSON: " + e.getMessage());
        }
        return 0.0;
    }
}