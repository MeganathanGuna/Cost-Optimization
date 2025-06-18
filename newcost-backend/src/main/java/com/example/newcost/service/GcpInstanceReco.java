package com.example.newcost.service;

import com.example.newcost.model.GcpCredentialsRequest;
import com.example.newcost.model.GcpCredentialsRequest;
import com.example.newcost.model.GcpInstanceRecom;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.recommender.v1.*;
import com.google.protobuf.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

@Service
public class GcpInstanceReco {

    public List<GcpInstanceRecom> getRecommendations(GcpCredentialsRequest creds) throws IOException {
        List<GcpInstanceRecom> result = new ArrayList<>();

        try (RecommenderClient recommenderClient = RecommenderClient.create(RecommenderSettings.newBuilder()
                .setCredentialsProvider(() -> getCredentialsFromDto(creds))
                .build())) {

            String parent = String.format("projects/%s/locations/global/recommenders/google.compute.instance.MachineTypeRecommender", creds.getProjectId());
            RecommenderName recommenderName = RecommenderName.parse(parent);

            for (Recommendation recommendation : recommenderClient.listRecommendations(recommenderName).iterateAll()) {
                RecommendationContent content = recommendation.getContent();

                if (content == null || content.getOperationGroupsList().isEmpty()) continue;

                String instanceId = "";
                String currentType = "";
                String recommendedType = "";
                String zone = extractZoneFromName(recommendation.getName());

                for (OperationGroup group : content.getOperationGroupsList()) {
                    for (Operation op : group.getOperationsList()) {
                        String path = op.getPath();
                        Value value = op.getValue();

                        switch (path) {
                            case "/machineType" -> currentType = value.getStringValue();
                            case "/instance" -> instanceId = value.getStringValue();
                            case "/recommendedMachineType" -> recommendedType = value.getStringValue();
                        }
                    }
                }

                double currentPrice = getPrice(currentType, zone);
                double recommendedPrice = getPrice(recommendedType, zone);
                double monthlySavings = (currentPrice - recommendedPrice) * 730;

                result.add(new GcpInstanceRecom(
                        instanceId,
                        zone,
                        currentType,
                        recommendedType,
                        currentPrice,
                        recommendedPrice,
                        monthlySavings,
                        recommendation.getDescription()
                ));
            }
        }

        return result;
    }

    private ServiceAccountCredentials getCredentialsFromDto(GcpCredentialsRequest dto) throws IOException {
        String jsonKey = """
        {
            "type": "service_account",
            "project_id": "%s",
            "private_key_id": "%s",
            "private_key": "%s",
            "client_email": "%s",
            "client_id": "%s",
            "auth_uri": "https://accounts.google.com/o/oauth2/auth",
            "token_uri": "https://oauth2.googleapis.com/token",
            "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
            "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/%s"
        }
        """.formatted(
                dto.getProjectId(),
                dto.getPrivateKeyId(),
                dto.getPrivateKey().replace("\\n", "\n"),
                dto.getClientEmail(),
                dto.getClientId(),
                dto.getClientEmail().replace("@", "%40")
        );

        return ServiceAccountCredentials.fromStream(new ByteArrayInputStream(jsonKey.getBytes()));
    }

    private double getPrice(String machineType, String zone) {
        return switch (machineType) {
            case "n1-standard-1" -> 0.0475;
            case "n1-standard-2" -> 0.0950;
            case "e2-medium" -> 0.0210;
            case "e2-standard-2" -> 0.0670;
            default -> 0.0;
        };
    }

    private String extractZoneFromName(String name) {
        String[] parts = name.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].equals("locations")) return parts[i + 1];
        }
        return "unknown";
    }
}
