package com.example.newcost.config;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.AwsRegionProvider;
import com.amazonaws.services.costexplorer.AWSCostExplorer;
import com.amazonaws.services.costexplorer.AWSCostExplorerClientBuilder;
import com.example.newcost.awscontext.AwsRequestContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.annotation.RequestScope;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.computeoptimizer.ComputeOptimizerClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.pricing.PricingClient;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsConfig {

    @Bean
    @RequestScope
    public AwsCredentialsProvider awsCredentialsProvider(
            @RequestHeader(name = "X-AWS-AccessKey", required = false) String accessKey,
            @RequestHeader(name = "X-AWS-SecretKey", required = false) String secretKey,
            @Value("${aws.accessKeyId}") String defaultAccessKey,
            @Value("${aws.secretKey}") String defaultSecretKey) {

        String finalAccessKey = accessKey != null ? accessKey : defaultAccessKey;
        String finalSecretKey = secretKey != null ? secretKey : defaultSecretKey;

        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(finalAccessKey, finalSecretKey)
        );
    }


    @Bean
    public AwsRegionProvider regionProvider() {
        return new AwsRegionProvider() {
            @Override
            public String getRegion() {
                return null; // Force explicit region setting
            }
        };
    }

    // For AWS SDK v1 services (like Cost Explorer)
    @Bean
    @RequestScope
    public AWSCredentialsProvider awsV1CredentialsProvider(
            @RequestHeader("X-AWS-AccessKey") String accessKey,
            @RequestHeader("X-AWS-SecretKey") String secretKey) {
        return new AWSStaticCredentialsProvider(
                new BasicAWSCredentials(accessKey, secretKey)
        );
    }

    @Bean
    @RequestScope
    public Ec2Client ec2Client(AwsRequestContext context) {
        return Ec2Client.builder()
                .region(Region.of(context.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(context.getAccessKey(), context.getSecretKey())
                ))
                .build();
    }

    @Bean
    @RequestScope
    public S3Client s3Client(AwsRequestContext context) {
        return S3Client.builder()
                .region(Region.of(context.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(context.getAccessKey(), context.getSecretKey())
                ))
                .build();
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    public ComputeOptimizerClient computeOptimizerClient(AwsRequestContext context) {
        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(context.getAccessKey(), context.getSecretKey());

        return ComputeOptimizerClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .region(Region.of(context.getRegion()))
                .build();
    }

    @Bean
    @RequestScope
    public CloudWatchClient cloudWatchClient(AwsRequestContext context) {
        return CloudWatchClient.builder()
                .region(Region.of(context.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(context.getAccessKey(), context.getSecretKey())
                ))
                .build();
    }

    @Bean
    @RequestScope
    public PricingClient pricingClient(AwsCredentialsProvider credentialsProvider) {
        return PricingClient.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.AP_SOUTH_1)
                .build();
    }

    @Bean
    @RequestScope
    public AWSCostExplorer awsCostExplorer(AwsRequestContext context) {
        return AWSCostExplorerClientBuilder.standard()
                .withRegion(context.getRegion())
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(context.getAccessKey(), context.getSecretKey())))
                .build();
    }

    @Bean
    @RequestScope
    public RdsClient rdsClient(AwsRequestContext context) {
        return RdsClient.builder()
                .region(Region.of(context.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(context.getAccessKey(), context.getSecretKey())
                ))
                .build();
    }
}