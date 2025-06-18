package com.example.newcost.controller;

import com.example.newcost.model.*;
import com.example.newcost.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestBody;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.*;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;
import software.amazon.awssdk.services.computeoptimizer.model.InstanceRecommendation;
import software.amazon.awssdk.services.computeoptimizer.model.InstanceRecommendationOption;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockagentruntime.model.*;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
public class AwsController {

    private static final Logger logger = LoggerFactory.getLogger(AwsController.class);

    private final AwsCostService awsCostService;
    private final Ec2Service ec2Service;
    private final S3Service s3Service;
    private final ComputeOptimizerService computeOptimizerService;
    private final Ec2Client ec2Client;
    private final GcpInstanceReco recommendationService;
    private final RdsService rdsService;
    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final BedrockAgentRuntimeClient bedrockAgentRuntimeClient;

    @Autowired
    public AwsController(Ec2Service ec2Service,
                         S3Service s3Service,
                         ComputeOptimizerService computeOptimizerService,
                         Ec2Client ec2Client,
                         AwsCostService awsCostService,
                         GcpInstanceReco recommendationService,
                         RdsService rdsService) {
        this.ec2Service = ec2Service;
        this.s3Service = s3Service;
        this.computeOptimizerService = computeOptimizerService;
        this.ec2Client = ec2Client;
        this.awsCostService = awsCostService;
        this.recommendationService = recommendationService;
        this.rdsService = rdsService;
        this.bedrockRuntimeClient = BedrockRuntimeClient.builder()
                .region(Region.US_EAST_1)
                .build();
        this.bedrockAgentRuntimeClient = BedrockAgentRuntimeClient.builder()
                .region(Region.US_EAST_1)
                .build();
    }

    @GetMapping("/cost-data")
    public ResponseEntity<CostDataDTO> getCostData(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestHeader("X-AWS-AccessKey") String accessKey,
            @RequestHeader("X-AWS-SecretKey") String secretKey) {
        try {
            LocalDate today = LocalDate.now();
            int selectedMonth = month != null ? month : today.getMonthValue();
            int selectedYear = year != null ? year : today.getYear();
            CostDataDTO costData = awsCostService.getCostData(selectedMonth, selectedYear);
            return ResponseEntity.ok(costData);
        } catch (Exception e) {
            logger.error("Error fetching cost data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/instance")
    public ResponseEntity<List<InstanceRecommendationDTO>> getEc2InstanceRecommendations(
            @RequestHeader("X-AWS-AccessKey") String accessKey,
            @RequestHeader("X-AWS-SecretKey") String secretKey,
            @RequestHeader("X-AWS-Region") String regionHeader) {
        try {
            Map<String, Double> priceCache = new HashMap<>();
            priceCache.put("t2.medium", 0.0464);
            priceCache.put("t4g.medium", 0.0336);
            priceCache.put("m5.large", 0.096);
            priceCache.put("c5.xlarge", 0.17);

            List<InstanceRecommendation> recommendations = computeOptimizerService.getEc2InstanceRecommendations();
            List<Instance> ec2Instances = ec2Service.listInstances();

            List<InstanceRecommendationDTO> result = recommendations.stream().map(rec -> {
                String instanceArn = rec.instanceArn();
                String instanceId = instanceArn.substring(instanceArn.lastIndexOf("/") + 1);

                Instance matchedInstance = ec2Instances.stream()
                        .filter(i -> i.instanceId().equals(instanceId))
                        .findFirst()
                        .orElse(null);

                String instanceName = "";
                String currentInstanceType = "";
                String currentState = "";
                String region = regionHeader;

                if (matchedInstance != null) {
                    currentInstanceType = matchedInstance.instanceTypeAsString();
                    currentState = matchedInstance.state().nameAsString();
                    instanceName = matchedInstance.tags().stream()
                            .filter(tag -> tag.key().equalsIgnoreCase("Name"))
                            .map(tag -> tag.value())
                            .findFirst()
                            .orElse("Unnamed");
                }

                double currentPrice = computeOptimizerService.getOnDemandPrice(currentInstanceType, region);
                if (currentPrice == 0.0 && priceCache.containsKey(currentInstanceType)) {
                    currentPrice = priceCache.get(currentInstanceType);
                }

                String recommendedType = "No recommendation";
                double recommendedPrice = 0.0;
                double monthlySavings = 0.0;

                if (rec.recommendationOptions() != null && !rec.recommendationOptions().isEmpty()) {
                    InstanceRecommendationOption bestOption = rec.recommendationOptions().stream()
                            .min(Comparator.comparingDouble(InstanceRecommendationOption::performanceRisk))
                            .orElse(rec.recommendationOptions().get(0));

                    recommendedType = bestOption.instanceType();
                    recommendedPrice = computeOptimizerService.getOnDemandPrice(recommendedType, region);
                    if (recommendedPrice == 0.0 && priceCache.containsKey(recommendedType)) {
                        recommendedPrice = priceCache.get(recommendedType);
                    }

                    monthlySavings = (currentPrice - recommendedPrice) * 730;
                }

                return new InstanceRecommendationDTO(
                        instanceId,
                        instanceName,
                        rec.findingAsString(),
                        currentState,
                        currentInstanceType,
                        currentPrice,
                        recommendedType,
                        recommendedPrice,
                        monthlySavings,
                        region
                );
            }).collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching EC2 recommendations: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/volumes")
    public ResponseEntity<List<VolumeDTO>> getEbsVolumes(
            @RequestHeader("X-AWS-AccessKey") String accessKey,
            @RequestHeader("X-AWS-SecretKey") String secretKey,
            @RequestHeader("X-AWS-Region") String regionHeader) {
        try {
            List<VolumeDTO> volumes = ec2Service.listEbsVolumes().stream()
                    .map(volume -> new VolumeDTO(
                            volume.volumeId(),
                            String.valueOf(volume.size()),
                            volume.state().toString(),
                            volume.attachments().isEmpty() ? "Detached" : volume.attachments().get(0).state().toString(),
                            volume.volumeTypeAsString()
                    ))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(volumes);
        } catch (Exception e) {
            logger.error("Error fetching EBS volumes: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/snapshots")
    public ResponseEntity<List<Map<String, Object>>> getEbsSnapshots(
            @RequestHeader("X-AWS-AccessKey") String accessKey,
            @RequestHeader("X-AWS-SecretKey") String secretKey,
            @RequestHeader("X-AWS-Region") String regionHeader) {
        try {
            List<Map<String, Object>> snapshots = ec2Service.listEbsSnapshotsDetailed();
            return ResponseEntity.ok(snapshots);
        } catch (Exception e) {
            logger.error("Error fetching EBS snapshots: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/elastic-ips")
    public ResponseEntity<List<ElasticIpDTO>> getElasticIps(
            @RequestHeader("X-AWS-AccessKey") String accessKey,
            @RequestHeader("X-AWS-SecretKey") String secretKey,
            @RequestHeader("X-AWS-Region") String regionHeader) {
        try {
            List<ElasticIpDTO> elasticIps = ec2Client.describeAddresses().addresses().stream()
                    .map(ElasticIpDTO::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(elasticIps);
        } catch (Exception e) {
            logger.error("Error fetching Elastic IPs: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/buckets")
    public ResponseEntity<List<S3BucketDTO>> getS3Buckets() {
        try {
            List<S3BucketDTO> buckets = s3Service.listBuckets();
            return ResponseEntity.ok(buckets);
        } catch (Exception e) {
            logger.error("Error retrieving S3 buckets: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validateCredentials(@RequestBody AwsCredentialsRequest request) {
        try {
            Ec2Client ec2Client = Ec2Client.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(request.getAccessKey(), request.getSecretKey())))
                    .region(Region.of(request.getRegion()))
                    .build();
            ec2Client.describeRegions();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error validating credentials: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid AWS credentials: " + e.getMessage());
        }
    }

    @GetMapping("/saveebs")
    public ResponseEntity<List<Map<String, Object>>> getVolumeSavings() {
        try {
            List<Map<String, Object>> savings = ec2Service.getVolumeSavingsSuggestions();
            return ResponseEntity.ok(savings);
        } catch (Exception e) {
            logger.error("Error retrieving volume savings: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/elastic-ip-savings")
    public ResponseEntity<List<Map<String, Object>>> getElasticIpSavings() {
        try {
            List<Map<String, Object>> savings = ec2Service.getElasticIpSavingsSuggestions();
            return ResponseEntity.ok(savings);
        } catch (Exception e) {
            logger.error("Error retrieving Elastic IP savings: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/gcp-ec2")
    public ResponseEntity<List<GcpInstanceRecom>> getGcpRecommendations(@RequestBody GcpCredentialsRequest request) {
        try {
            List<GcpInstanceRecom> recommendations = recommendationService.getRecommendations(request);
            return ResponseEntity.ok(recommendations);
        } catch (IOException e) {
            logger.error("Error retrieving GCP recommendations: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/rds-recommendations")
    public ResponseEntity<List<RdsRecommendationDTO>> getRdsRecommendations(
            @RequestHeader("X-AWS-AccessKey") String accessKey,
            @RequestHeader("X-AWS-SecretKey") String secretKey,
            @RequestHeader("X-AWS-Region") String regionHeader) {
        try {
            List<RdsRecommendationDTO> recommendations = rdsService.getRdsRecommendations();
            return ResponseEntity.ok(recommendations);
        } catch (Exception e) {
            logger.error("Error retrieving RDS recommendations: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/help-desk")
    public ResponseEntity<String> getHelpDeskResponse(
            @RequestParam String query,
            @RequestHeader("X-AWS-AccessKey") String accessKey,
            @RequestHeader("X-AWS-SecretKey") String secretKey) {

        String modelId = "meta.llama3-70b-instruct-v1:0"; // Confirm exact model ID
        String knowledgeBaseId = "KWG4S1N8IX"; // Replace with your actual KB ID

        try {
            BedrockAgentRuntimeClient agentClient = BedrockAgentRuntimeClient.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    .region(Region.AP_SOUTH_1)
                    .build();

            // Corrected request with required type field
            RetrieveAndGenerateRequest request = RetrieveAndGenerateRequest.builder()
                    .input(builder -> builder.text(query))
                    .retrieveAndGenerateConfiguration(builder -> builder
                            .type(RetrieveAndGenerateType.KNOWLEDGE_BASE) // REQUIRED FIELD
                            .knowledgeBaseConfiguration(kbConfig -> kbConfig
                                    .knowledgeBaseId(knowledgeBaseId)
                                    .modelArn("arn:aws:bedrock:ap-south-1::foundation-model/" + modelId)
                            )
                    )
                    .build();

            RetrieveAndGenerateResponse response = agentClient.retrieveAndGenerate(request);
            System.out.println("Retrieved Text: " + response.output().text());
            System.out.println("Retrieved Citations: " + response.citations());
            String answer = response.output().text();

            return ResponseEntity.ok(answer);

        } catch (Exception e) {
            logger.error("Help desk error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }
}