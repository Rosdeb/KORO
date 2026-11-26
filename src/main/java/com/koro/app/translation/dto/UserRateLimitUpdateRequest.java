package com.koro.app.translation.dto;

public class UserRateLimitUpdateRequest {
    private Integer customDailyLimit;
    private Integer customMonthlyLimit;
    private boolean resetDailyCount;
    private boolean resetMonthlyCount;

    public UserRateLimitUpdateRequest() {
    }

    public UserRateLimitUpdateRequest(Integer customDailyLimit, Integer customMonthlyLimit,
                                     boolean resetDailyCount, boolean resetMonthlyCount) {
        this.customDailyLimit = customDailyLimit;
        this.customMonthlyLimit = customMonthlyLimit;
        this.resetDailyCount = resetDailyCount;
        this.resetMonthlyCount = resetMonthlyCount;
    }

    public Integer getCustomDailyLimit() {
        return customDailyLimit;
    }

    public void setCustomDailyLimit(Integer customDailyLimit) {
        this.customDailyLimit = customDailyLimit;
    }

    public Integer getCustomMonthlyLimit() {
        return customMonthlyLimit;
    }

    public void setCustomMonthlyLimit(Integer customMonthlyLimit) {
        this.customMonthlyLimit = customMonthlyLimit;
    }

    public boolean isResetDailyCount() {
        return resetDailyCount;
    }

    public void setResetDailyCount(boolean resetDailyCount) {
        this.resetDailyCount = resetDailyCount;
    }

    public boolean isResetMonthlyCount() {
        return resetMonthlyCount;
    }

    public void setResetMonthlyCount(boolean resetMonthlyCount) {
        this.resetMonthlyCount = resetMonthlyCount;
    }
}
