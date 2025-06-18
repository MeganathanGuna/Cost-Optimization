package com.example.newcost.model;

public class GcpInstanceRecom {

    private String instanceId;
    private String zone;
    private String currentMachineType;
    private String recommendedMachineType;
    private double currentCost;
    private double recommendedCost;
    private double potentialMonthlySavings;
    private String recommendationReason;

    public GcpInstanceRecom(String instanceId, String zone, String currentMachineType,
                            String recommendedMachineType, double currentCost,
                            double recommendedCost, double potentialMonthlySavings,
                            String recommendationReason) {
        this.instanceId = instanceId;
        this.zone = zone;
        this.currentMachineType = currentMachineType;
        this.recommendedMachineType = recommendedMachineType;
        this.currentCost = currentCost;
        this.recommendedCost = recommendedCost;
        this.potentialMonthlySavings = potentialMonthlySavings;
        this.recommendationReason = recommendationReason;
    }

}
