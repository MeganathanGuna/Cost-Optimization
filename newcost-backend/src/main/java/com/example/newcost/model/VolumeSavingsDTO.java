package com.example.newcost.model;

public class VolumeSavingsDTO {

    private String volumeId;
    private String reason;

    public String getVolumeId() {
        return volumeId;
    }

    public void setVolumeId(String volumeId) {
        this.volumeId = volumeId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public double getMonthlyCost() {
        return monthlyCost;
    }

    public void setMonthlyCost(double monthlyCost) {
        this.monthlyCost = monthlyCost;
    }

    public double getPotentialSavings() {
        return potentialSavings;
    }

    public void setPotentialSavings(double potentialSavings) {
        this.potentialSavings = potentialSavings;
    }

    private double monthlyCost;
    private double potentialSavings;
}
