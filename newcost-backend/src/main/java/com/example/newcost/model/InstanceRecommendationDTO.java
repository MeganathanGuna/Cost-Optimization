package com.example.newcost.model;

public class InstanceRecommendationDTO {
    private String instanceId;
    private String instanceName;
    private String finding;
    private String currentState;
    private String currentInstanceType;
    private double currentOnDemandPrice;
    private String recommendedInstanceType;
    private double recommendedOnDemandPrice;
    private double estimatedMonthlySavings;
    private String region;

    // Constructors
    public InstanceRecommendationDTO() {}

    public InstanceRecommendationDTO(String instanceId, String instanceName, String finding,
                                     String currentState, String currentInstanceType,
                                     double currentOnDemandPrice, String recommendedInstanceType,
                                     double recommendedOnDemandPrice, double estimatedMonthlySavings,
                                     String region) {
        this.instanceId = instanceId;
        this.instanceName = instanceName;
        this.finding = finding;
        this.currentState = currentState;
        this.currentInstanceType = currentInstanceType;
        this.currentOnDemandPrice = currentOnDemandPrice;
        this.recommendedInstanceType = recommendedInstanceType;
        this.recommendedOnDemandPrice = recommendedOnDemandPrice;
        this.estimatedMonthlySavings = estimatedMonthlySavings;
        this.region = region;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public double getRecommendedOnDemandPrice() {
        return recommendedOnDemandPrice;
    }

    public void setRecommendedOnDemandPrice(double recommendedOnDemandPrice) {
        this.recommendedOnDemandPrice = recommendedOnDemandPrice;
    }

    public String getRecommendedInstanceType() {
        return recommendedInstanceType;
    }

    public void setRecommendedInstanceType(String recommendedInstanceType) {
        this.recommendedInstanceType = recommendedInstanceType;
    }

    public double getCurrentOnDemandPrice() {
        return currentOnDemandPrice;
    }

    public void setCurrentOnDemandPrice(double currentOnDemandPrice) {
        this.currentOnDemandPrice = currentOnDemandPrice;
    }

    public String getCurrentInstanceType() {
        return currentInstanceType;
    }

    public void setCurrentInstanceType(String currentInstanceType) {
        this.currentInstanceType = currentInstanceType;
    }

    public String getCurrentState() {
        return currentState;
    }

    public void setCurrentState(String currentState) {
        this.currentState = currentState;
    }

    public String getFinding() {
        return finding;
    }

    public void setFinding(String finding) {
        this.finding = finding;
    }

    // Getters and Setters
    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    // ... (other getters and setters for all fields) ...

    public double getEstimatedMonthlySavings() {
        return estimatedMonthlySavings;
    }

    public void setEstimatedMonthlySavings(double estimatedMonthlySavings) {
        this.estimatedMonthlySavings = estimatedMonthlySavings;
    }
}