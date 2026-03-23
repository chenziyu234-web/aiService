package com.logistics.backend.furniture.model;

public class AddressResult {

    private boolean success;
    private double longitude;
    private double latitude;
    private String matchedAddress;
    private String suggestion;

    public AddressResult() {}

    public static AddressResult success(double longitude, double latitude, String matchedAddress) {
        AddressResult r = new AddressResult();
        r.success = true;
        r.longitude = longitude;
        r.latitude = latitude;
        r.matchedAddress = matchedAddress;
        return r;
    }

    public static AddressResult fail(String suggestion) {
        AddressResult r = new AddressResult();
        r.success = false;
        r.suggestion = suggestion;
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public String getMatchedAddress() { return matchedAddress; }
    public void setMatchedAddress(String matchedAddress) { this.matchedAddress = matchedAddress; }

    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
}
