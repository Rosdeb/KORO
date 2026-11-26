package com.koro.app.translation.dto;

public class RateLimitResult {
    private boolean allowed;
    private String message;
    private int dailyLimit;
    private int dailyRemaining;
    private int monthlyLimit;
    private int monthlyRemaining;

    public RateLimitResult() {
    }

    public RateLimitResult(boolean allowed, String message, int dailyLimit, int dailyRemaining, int monthlyLimit, int monthlyRemaining) {
        this.allowed = allowed;
        this.message = message;
        this.dailyLimit = dailyLimit;
        this.dailyRemaining = dailyRemaining;
        this.monthlyLimit = monthlyLimit;
        this.monthlyRemaining = monthlyRemaining;
    }

    public static RateLimitResult allowed(int dailyLimit, int dailyRemaining, int monthlyLimit, int monthlyRemaining) {
        return new RateLimitResult(true, null, dailyLimit, dailyRemaining, monthlyLimit, monthlyRemaining);
    }

    public static RateLimitResult denied(String message, int dailyLimit, int dailyRemaining, int monthlyLimit, int monthlyRemaining) {
        return new RateLimitResult(false, message, dailyLimit, dailyRemaining, monthlyLimit, monthlyRemaining);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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
}
