package com.example.newcost.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class S3BucketDTO {
    @JsonProperty("bucket_name")
    private String bucketName;

    @JsonProperty("bucket_size")
    private String bucketSize;  // Size of the bucket

    @JsonProperty("bucket_type")
    private String bucketType;  // For example, Standard, Intelligent-Tiering, etc.

    @JsonProperty("region")
    private String region;  // The region where the bucket is located

    @JsonProperty("recommendation")
    private String recommendation;  // This could be a recommendation like moving to a different storage class, etc.

    @JsonProperty("estimated_savings")
    private String estimatedSavings;

    public String getEstimatedSavings() {
        return estimatedSavings;
    }

    public void setEstimatedSavings(String estimatedSavings) {
        this.estimatedSavings = estimatedSavings;
    }

    // Constructor
    public S3BucketDTO(String bucketName, String bucketSize, String bucketType, String region, String recommendation, String estimatedSavings) {
        this.bucketName = bucketName;
        this.bucketSize = bucketSize;
        this.bucketType = bucketType;
        this.region = region;
        this.recommendation = recommendation;
        this.estimatedSavings = estimatedSavings;
    }

    // Getters and Setters
    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getBucketSize() {
        return bucketSize;
    }

    public void setBucketSize(String bucketSize) {
        this.bucketSize = bucketSize;
    }

    public String getBucketType() {
        return bucketType;
    }

    public void setBucketType(String bucketType) {
        this.bucketType = bucketType;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    @Override
    public String toString() {
        return "S3BucketDTO [bucketName=" + bucketName + ", bucketSize=" + bucketSize + ", bucketType=" + bucketType +
                ", region=" + region + ", recommendation=" + recommendation + "]";
    }
}
