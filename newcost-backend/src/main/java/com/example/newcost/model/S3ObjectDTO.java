package com.example.newcost.model;

public class S3ObjectDTO {
    private String ObjectName;
    private String lastModified;
    private String eTag;

    // Constructor
    public S3ObjectDTO(String ObjectName, String lastModified, String eTag) {
        this.ObjectName = ObjectName;
        this.lastModified = lastModified;
        this.eTag = eTag;
    }

    public String getObjectName() {
        return ObjectName;
    }

    public void setObjectName(String objectName) {
        ObjectName = objectName;
    }

    // Getters and Setters
    public String ObjectName() {
        return ObjectName;
    }

    public void ObjectName(String key) {
        this.ObjectName = key;
    }

    public String getLastModified() {
        return lastModified;
    }

    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }

    public String getETag() {
        return eTag;
    }


    public void setETag(String eTag) {
        this.eTag = eTag;
    }
}

