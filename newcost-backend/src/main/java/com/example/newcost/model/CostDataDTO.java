package com.example.newcost.model;

public class CostDataDTO {
    private String estimatedGrandTotal = "0.00";
    private String highestRegionSpend = "0.00";
    private String highestRegionName = "N/A";
    private String highestServiceSpend = "0.00";
    private String highestServiceName = "N/A";

    // Getters and setters
    public String getEstimatedGrandTotal() {
        return estimatedGrandTotal;
    }

    public void setEstimatedGrandTotal(String estimatedGrandTotal) {
        this.estimatedGrandTotal = estimatedGrandTotal;
    }

    public String getHighestRegionSpend() {
        return highestRegionSpend;
    }

    public void setHighestRegionSpend(String highestRegionSpend) {
        this.highestRegionSpend = highestRegionSpend;
    }

    public String getHighestRegionName() {
        return highestRegionName;
    }

    public void setHighestRegionName(String highestRegionName) {
        this.highestRegionName = highestRegionName;
    }

    public String getHighestServiceSpend() {
        return highestServiceSpend;
    }

    public void setHighestServiceSpend(String highestServiceSpend) {
        this.highestServiceSpend = highestServiceSpend;
    }

    public String getHighestServiceName() {
        return highestServiceName;
    }

    public void setHighestServiceName(String highestServiceName) {
        this.highestServiceName = highestServiceName;
    }
}
