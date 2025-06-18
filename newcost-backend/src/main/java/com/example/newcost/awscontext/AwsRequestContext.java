package com.example.newcost.awscontext;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class AwsRequestContext {

    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final String accountId;

    public AwsRequestContext(HttpServletRequest request) {
        this.region = request.getHeader("X-AWS-Region");
        this.accessKey = request.getHeader("X-AWS-AccessKey");
        this.secretKey = request.getHeader("X-AWS-SecretKey");
        this.accountId = request.getHeader("X-AWS-AccountId");
    }

    public String getRegion() {
        return region;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getAccountId() {
        return accountId;
    }
}
