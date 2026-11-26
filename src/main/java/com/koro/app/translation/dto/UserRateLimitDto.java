package com.koro.app.translation.dto;

import java.time.Instant;

public class UserRateLimitDto {
    private String userId;
    private String userName;
    private String userEmail;
    private int dailyCount;
    private int dailyLimit;
    private int dailyRemaining;
    private int monthlyCount;
    private int monthlyLimit;
    private int monthlyRemaining;
    private Integer customDailyLimit;
    private Integer customMonthlyLimit;
    private Instant lastSearchAt;

    public UserRateLimitDto() {
    }

    public UserRateLimitDto(String userId, String userName, String userEmail, int dailyCount, int dailyLimit,
                            int dailyRemaining, int monthlyCount, int monthlyLimit, int monthlyRemaining,
                            Integer customDailyLimit, Integer customMonthlyLimit, Instant lastSearchAt) {
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.dailyCount = dailyCount;
        this.dailyLimit = dailyLimit;
        this.dailyRemaining = dailyRemaining;
        this.monthlyCount = monthlyCount;
        this.monthlyLimit = monthlyLimit;
        this.monthlyRemaining = monthlyRemaining;
        this.customDailyLimit = customDailyLimit;
        this.customMonthlyLimit = customMonthlyLimit;
        this.lastSearchAt = lastSearchAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public int getDailyCount() {
        return dailyCount;
    }

    public void setDailyCount(int dailyCount) {
        this.dailyCount = dailyCount;
    }

    public int getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public int getDailyRemaining() {
        return dailyRemaining;
    }

    public void setDailyRemaining(int dailyRemaining) {
        this.dailyRemaining = dailyRemaining;
    }

    public int getMonthlyCount() {
        return monthlyCount;
    }

    public void setMonthlyCount(int monthlyCount) {
        this.monthlyCount = monthlyCount;
    }

    public int getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(int monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public int getMonthlyRemaining() {
        return monthlyRemaining;
    }

    public void setMonthlyRemaining(int monthlyRemaining) {
        this.monthlyRemaining = monthlyRemaining;
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

    public Instant getLastSearchAt() {
        return lastSearchAt;
    }

    public void setLastSearchAt(Instant lastSearchAt) {
        this.lastSearchAt = lastSearchAt;
    }
}
