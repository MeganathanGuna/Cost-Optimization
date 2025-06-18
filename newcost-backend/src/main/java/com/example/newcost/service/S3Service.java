package com.example.newcost.service;

import com.example.newcost.model.S3BucketDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class S3Service {

    private final S3Client s3Client;

    @Autowired
    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public List<S3BucketDTO> listBuckets() {
        ListBucketsRequest request = ListBucketsRequest.builder().build();
        ListBucketsResponse response = s3Client.listBuckets(request);

        return response.buckets().stream()
                .map(bucket -> {
                    long sizeInBytes = getBucketSizeInBytes(bucket.name());
                    String bucketType = getBucketType(bucket.name()); // Call once
                    String region = getBucketRegion(bucket.name());
                    String recommendation = getBucketRecommendation(bucket.name(), sizeInBytes);
                    String estimatedSavings = estimateMonthlySavings(bucketType, sizeInBytes);

                    return new S3BucketDTO(
                            bucket.name(),
                            formatStorageSize(sizeInBytes),
                            bucketType,
                            region,
                            recommendation,
                            estimatedSavings
                    );
                })
                .collect(Collectors.toList());
    }

    // Python-like storage size formatter
    private String formatStorageSize(long sizeInBytes) {
        if (sizeInBytes < 1024) {
            return String.format("%d Bytes", sizeInBytes);
        } else if (sizeInBytes < 1024 * 1024) {
            return String.format("%.2f KB", sizeInBytes / 1024.0);
        } else if (sizeInBytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", sizeInBytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", sizeInBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private String getBucketType(String bucketName) {
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(1)
                    .build();
            ListObjectsV2Response response = s3Client.listObjectsV2(request);

            if (!response.contents().isEmpty()) {
                return response.contents().get(0).storageClassAsString();
            }
        } catch (Exception e) {
            // Optionally log error
        }

        return "STANDARD"; // Assume STANDARD if unknown or error
    }


    private String getBucketRegion(String bucketName) {
        GetBucketLocationRequest request = GetBucketLocationRequest.builder()
                .bucket(bucketName)
                .build();

        try {
            GetBucketLocationResponse response = s3Client.getBucketLocation(request);
            String regionCode = response.locationConstraintAsString();

            // Handle special cases
            if (regionCode == null || regionCode.isEmpty()) {
                return "us-east-1";  // Default region (N. Virginia)
            } else if ("EU".equalsIgnoreCase(regionCode)) {
                return "eu-west-1";   // AWS uses "EU" for Ireland
            } else {
                return regionCode;    // Return actual region (e.g., "ap-south-1")
            }
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to fetch bucket region: " + e.getMessage(), e);
        }
    }

    private String getBucketRecommendation(String bucketName, long sizeInBytes) {
        String bucketType = getBucketType(bucketName);

        if ("STANDARD".equals(bucketType)){
            if (sizeInBytes > 1_000_000_000) { // > 1GB
                return "Consider moving to Intelligent-Tiering for cost savings";
            } else if (sizeInBytes > 100_000_000) { // > 100MB
                return "Consider Standard-IA for infrequently accessed data";
            }
        }
        return "No recommendation";
    }

    private long getBucketSizeInBytes(String bucketName) {
        long totalSize = 0;
        String continuationToken = null;

        do {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .continuationToken(continuationToken)
                    .build();

            ListObjectsV2Response response = s3Client.listObjectsV2(request);
            totalSize += response.contents().stream()
                    .mapToLong(S3Object::size)
                    .sum();

            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);

        return totalSize;
    }

    private String estimateMonthlySavings(String storageClass, long sizeInBytes) {
        // Prices per GB in USD (these are rough AWS averages)
        double standardRate = 0.023;
        double intelligentTieringRate = 0.0125;
        double standardIARate = 0.0125;

        double sizeInGB = sizeInBytes / (1024.0 * 1024.0 * 1024.0);

        double currentCost = sizeInGB * standardRate;
        double estimatedCost = currentCost;

        if ("STANDARD".equals(storageClass)) {
            if (sizeInBytes > 1_000_000_000) { // >1 GB
                estimatedCost = sizeInGB * intelligentTieringRate;
            } else if (sizeInBytes > 100_000_000) { // >100 MB
                estimatedCost = sizeInGB * standardIARate;
            }
        }

        double savings = currentCost - estimatedCost;
        return savings > 0 ? String.format("$%.2f", savings) : "$0.00";
    }



}