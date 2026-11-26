package com.koro.app.translation.dto;

public class RateLimitConfigDto {
    private boolean enabled;
    private int userDailyLimit;
    private int userMonthlyLimit;
    private int anonDailyLimit;
    private int anonMonthlyLimit;

    public RateLimitConfigDto() {
    }

    public RateLimitConfigDto(boolean enabled, int userDailyLimit, int userMonthlyLimit, int anonDailyLimit, int anonMonthlyLimit) {
        this.enabled = enabled;
        this.userDailyLimit = userDailyLimit;
        this.userMonthlyLimit = userMonthlyLimit;
        this.anonDailyLimit = anonDailyLimit;
        this.anonMonthlyLimit = anonMonthlyLimit;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getUserDailyLimit() {
        return userDailyLimit;
    }

    public void setUserDailyLimit(int userDailyLimit) {
        this.userDailyLimit = userDailyLimit;
    }

    public int getUserMonthlyLimit() {
        return userMonthlyLimit;
    }

    public void setUserMonthlyLimit(int userMonthlyLimit) {
        this.userMonthlyLimit = userMonthlyLimit;
    }

    public int getAnonDailyLimit() {
        return anonDailyLimit;
    }

    public void setAnonDailyLimit(int anonDailyLimit) {
        this.anonDailyLimit = anonDailyLimit;
    }

    public int getAnonMonthlyLimit() {
        return anonMonthlyLimit;
    }

    public void setAnonMonthlyLimit(int anonMonthlyLimit) {
        this.anonMonthlyLimit = anonMonthlyLimit;
    }
}
