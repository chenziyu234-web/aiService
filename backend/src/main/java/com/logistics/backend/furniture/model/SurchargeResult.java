package com.logistics.backend.furniture.model;

public class SurchargeResult {

    private boolean outOfRange;
    private double amount;
    private String ruleDescription;

    public SurchargeResult() {}

    public static SurchargeResult inRange() {
        SurchargeResult r = new SurchargeResult();
        r.outOfRange = false;
        r.amount = 0;
        r.ruleDescription = "在免费配送范围内";
        return r;
    }

    public static SurchargeResult surcharge(double amount, String rule) {
        SurchargeResult r = new SurchargeResult();
        r.outOfRange = true;
        r.amount = amount;
        r.ruleDescription = rule;
        return r;
    }

    public boolean isOutOfRange() { return outOfRange; }
    public void setOutOfRange(boolean outOfRange) { this.outOfRange = outOfRange; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getRuleDescription() { return ruleDescription; }
    public void setRuleDescription(String ruleDescription) { this.ruleDescription = ruleDescription; }
}
