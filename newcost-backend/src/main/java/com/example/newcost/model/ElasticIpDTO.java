package com.example.newcost.model;

import software.amazon.awssdk.services.ec2.model.Address;

public class ElasticIpDTO {
    private String publicIp;
    private String associationId;
    private String unassociationId; // "unassociationId" could mean null or absent.

    public ElasticIpDTO(Address address) {
        this.publicIp = address.publicIp();
        this.associationId = address.associationId();

        // If the IP is not associated, we'll set a placeholder for unassociationId.
        this.unassociationId = (address.associationId() == null) ? "No Association" : null;
    }

    // Getters and Setters
    public String getPublicIp() {
        return publicIp;
    }

    public void setPublicIp(String publicIp) {
        this.publicIp = publicIp;
    }

    public String getAssociationId() {
        return associationId;
    }

    public void setAssociationId(String associationId) {
        this.associationId = associationId;
    }

    public String getUnassociationId() {
        return unassociationId;
    }

    public void setUnassociationId(String unassociationId) {
        this.unassociationId = unassociationId;
    }
}
