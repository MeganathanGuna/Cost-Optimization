package com.example.newcost.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.ec2.model.Tag;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class Ec2Service {


    private static final Logger log = LoggerFactory.getLogger(Ec2Service.class);

    @Autowired
    private Ec2Client ec2Client;
    private final CloudWatchClient cloudWatchClient;

    @Autowired
    public Ec2Service(Ec2Client ec2Client, CloudWatchClient cloudWatchClient) {
        this.ec2Client = ec2Client;
        this.cloudWatchClient = cloudWatchClient;
    }

    // List all EBS volumes in the AWS account
    public List<Volume> listEbsVolumes() {
        DescribeVolumesRequest request = DescribeVolumesRequest.builder().build();
        DescribeVolumesResponse response = ec2Client.describeVolumes(request);
        return response.volumes();
    }

    // List all EBS snapshots
    public List<Map<String, Object>> listEbsSnapshotsDetailed() {
        DescribeSnapshotsRequest request = DescribeSnapshotsRequest.builder()
                .ownerIds("self")
                .build();

        DescribeSnapshotsResponse response = ec2Client.describeSnapshots(request);

        return response.snapshots().stream().map(snapshot -> {
            Map<String, Object> snapshotDetails = new HashMap<>();

            String name = snapshot.tags().stream()
                    .filter(tag -> tag.key().equalsIgnoreCase("Name"))
                    .map(Tag::value)
                    .findFirst()
                    .orElse("N/A");

            // Note: volumeSize is the original volume size, not actual storage used
            snapshotDetails.put("Name", name);
            snapshotDetails.put("SnapshotId", snapshot.snapshotId());
            snapshotDetails.put("VolumeSizeGB", snapshot.volumeSize());
            snapshotDetails.put("StorageTier", snapshot.storageTierAsString());
            snapshotDetails.put("Status", snapshot.stateAsString());
            snapshotDetails.put("StartTime", snapshot.startTime());

            // Add a note that actual storage used isn't available from this API
            double storageUsed = getSnapshotStorageUsage(snapshot.snapshotId());
            snapshotDetails.put("StorageUsedGB", storageUsed);

            return snapshotDetails;
        }).collect(Collectors.toList());
    }

    private double getSnapshotStorageUsage(String snapshotId) {
        try {
            Instant now = Instant.now();
            Instant startTime = now.minus(24, ChronoUnit.HOURS); // hour window

            log.info("Attempting to get CloudWatch metrics for snapshot: {}", snapshotId);
            log.debug("Time range: {} to {}", startTime, now);

            GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
                    .namespace("AWS/EBS")
                    .metricName("SnapshotStorageUsed")
                    .dimensions(Dimension.builder()
                            .name("SnapshotId")
                            .value(snapshotId)
                            .build())
                    .startTime(startTime)
                    .endTime(now)
                    .period(3600) // 1 hour intervals
                    .statistics(Statistic.AVERAGE)
                    .build();

            log.debug("CloudWatch request: {}", request);

            GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);
            log.debug("CloudWatch response received: {}", response);

            if (!response.datapoints().isEmpty()) {
                response.datapoints().forEach(dp ->
                        log.debug("Datapoint: {} = {} bytes", dp.timestamp(), dp.average()));

                double latestValue = response.datapoints().stream()
                        .max(Comparator.comparing(Datapoint::timestamp))
                        .map(dp -> dp.average() / (1024 * 1024 * 1024)) // Convert bytes to GB
                        .orElse(-1.0);

                log.info("Snapshot {} storage: {} GB", snapshotId, latestValue);
                return latestValue;
            } else {
                log.warn("No CloudWatch datapoints found for snapshot {}", snapshotId);
            }
        } catch (Exception e) {
            log.error("CloudWatch query failed for snapshot {}: {}", snapshotId, e.getMessage(), e);
        }
        return -1.0;
    }




    // List all Elastic IPs associated with the AWS account
    public List<Address> listElasticIps() {
        DescribeAddressesRequest request = DescribeAddressesRequest.builder().build();
        DescribeAddressesResponse response = ec2Client.describeAddresses(request);
        return response.addresses();
    }

    // List EC2 instances in the AWS account (add if needed)
    public List<Instance> listInstances() {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder().build();
        DescribeInstancesResponse response = ec2Client.describeInstances(request);
        return response.reservations().stream()
                .flatMap(reservation -> reservation.instances().stream())
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getVolumeSavingsSuggestions() {
        List<Volume> volumes = listEbsVolumes();

        Map<String, Double> pricing = Map.of(
                "gp2", 0.10,
                "gp3", 0.08,
                "io1", 0.125,
                "io2", 0.125,
                "sc1", 0.025,
                "st1", 0.045,
                "standard", 0.05
        );

        return volumes.stream().map(volume -> {
            Map<String, Object> suggestion = new HashMap<>();

            String volumeId = volume.volumeId();
            String type = volume.volumeTypeAsString();
            int size = volume.size(); // in GB
            boolean isAttached = !volume.attachments().isEmpty();

            double cost = pricing.getOrDefault(type, 0.10) * size;

            suggestion.put("VolumeId", volumeId);
            suggestion.put("SizeGB", size);
            suggestion.put("VolumeType", type);
            suggestion.put("MonthlyCostUSD", String.format("%.2f", cost));

            if (!isAttached) {
                suggestion.put("Reason", "Unattached EBS volume — can be deleted or snapshot");
                suggestion.put("PotentialSavingsUSD", String.format("%.2f", cost));
            } else if (type.equals("gp2")) {
                double gp3Cost = pricing.get("gp3") * size;
                double savings = cost - gp3Cost;
                suggestion.put("Reason", "Consider migrating from gp2 to gp3");
                suggestion.put("PotentialSavingsUSD", String.format("%.2f", savings));
            } else {
                suggestion.put("Reason", "No major savings opportunity detected");
                suggestion.put("PotentialSavingsUSD", "0.00");
            }

            return suggestion;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getElasticIpSavingsSuggestions() {
        List<Address> addresses = listElasticIps();
        double monthlyIdleIpCost = 0.005 * 24 * 30; // Approx. 3.60 USD/month

        return addresses.stream().map(address -> {
            Map<String, Object> suggestion = new HashMap<>();
            String ip = address.publicIp();
            String assocId = address.associationId();

            suggestion.put("PublicIp", ip);
            suggestion.put("AssociationId", assocId != null ? assocId : "None");

            if (assocId == null || assocId.isEmpty()) {
                suggestion.put("Reason", "Unassociated Elastic IP — you can release it to save costs");
                suggestion.put("PotentialSavingsUSD", String.format("%.2f", monthlyIdleIpCost));
            } else {
                suggestion.put("Reason", "Elastic IP in use — no savings opportunity");
                suggestion.put("PotentialSavingsUSD", "0.00");
            }

            return suggestion;
        }).collect(Collectors.toList());
    }




}
