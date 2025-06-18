package com.example.newcost.model;

import java.util.List;

public class RdsRecommendationDTO {

    private String dbInstanceIdentifier;
    private String dbClusterIdentifier;
    private String engine;
    private String utilization;
    private String finding;
    private List<String> currentInstanceTypes;

    public String getDbInstanceIdentifier() {
        return dbInstanceIdentifier;
    }

    public void setDbInstanceIdentifier(String dbInstanceIdentifier) {
        this.dbInstanceIdentifier = dbInstanceIdentifier;
    }

    public String getDbClusterIdentifier() {
        return dbClusterIdentifier;
    }

    public void setDbClusterIdentifier(String dbClusterIdentifier) {
        this.dbClusterIdentifier = dbClusterIdentifier;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getUtilization() {
        return utilization;
    }

    public void setUtilization(String utilization) {
        this.utilization = utilization;
    }

    public String getFinding() {
        return finding;
    }

    public void setFinding(String finding) {
        this.finding = finding;
    }

    public List<String> getCurrentInstanceTypes() {
        return currentInstanceTypes;
    }

    public void setCurrentInstanceTypes(List<String> currentInstanceTypes) {
        this.currentInstanceTypes = currentInstanceTypes;
    }

    public String getCurrentStorageType() {
        return currentStorageType;
    }

    public void setCurrentStorageType(String currentStorageType) {
        this.currentStorageType = currentStorageType;
    }

    public String getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(String currentPrice) {
        this.currentPrice = currentPrice;
    }

    public String getCurrentOndemandPrice() {
        return currentOndemandPrice;
    }

    public void setCurrentOndemandPrice(String currentOndemandPrice) {
        this.currentOndemandPrice = currentOndemandPrice;
    }

    public List<String> getRecommendedInstanceTypes() {
        return recommendedInstanceTypes;
    }

    public void setRecommendedInstanceTypes(List<String> recommendedInstanceTypes) {
        this.recommendedInstanceTypes = recommendedInstanceTypes;
    }

    public String getRecommendedPrice() {
        return recommendedPrice;
    }

    public void setRecommendedPrice(String recommendedPrice) {
        this.recommendedPrice = recommendedPrice;
    }

    public String getRecommendedOndemandPrice() {
        return recommendedOndemandPrice;
    }

    public void setRecommendedOndemandPrice(String recommendedOndemandPrice) {
        this.recommendedOndemandPrice = recommendedOndemandPrice;
    }

    private String currentStorageType;
    private String currentPrice;
    private String currentOndemandPrice;
    private List<String> recommendedInstanceTypes;
    private String recommendedPrice;
    private String recommendedOndemandPrice;
}
