package com.example.newcost.model;

public class VolumeDTO {
    private String volumeId;
    private String size;
    private String state;
    private String attachmentState;
    private String volumeType;  // New field

    public String getVolumeId() {
        return volumeId;
    }

    public void setVolumeId(String volumeId) {
        this.volumeId = volumeId;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getAttachmentState() {
        return attachmentState;
    }

    public void setAttachmentState(String attachmentState) {
        this.attachmentState = attachmentState;
    }

    public VolumeDTO(String volumeId, String size, String state,
                     String attachmentState, String volumeType) {
        this.volumeId = volumeId;
        this.size = size;
        this.state = state;
        this.attachmentState = attachmentState;
        this.volumeType = volumeType;
    }

    // Getters and Setters (add the new one)
    public String getVolumeType() {
        return volumeType;
    }

    public void setVolumeType(String volumeType) {
        this.volumeType = volumeType;
    }

    // ... keep all other existing getters/setters ...
}