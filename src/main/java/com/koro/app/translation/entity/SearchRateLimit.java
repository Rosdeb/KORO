package com.koro.app.translation.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "search_rate_limits")
public class SearchRateLimit {

    @Id
    private String id; // e.g. "USER:64e..." or "IP:103.xxx.xxx.xxx"

    @Indexed
    private String identifier; // raw user ID or IP address

    private String type; // "USER" or "IP"

    private String dailyDate; // "YYYY-MM-DD" in UTC
    private int dailyCount;

    private String monthlyMonth; // "YYYY-MM" in UTC
    private int monthlyCount;

    private Instant lastSearchAt;

    @LastModifiedDate
    private Instant updatedAt;

    public SearchRateLimit() {
    }

    public SearchRateLimit(String id, String identifier, String type, String dailyDate, int dailyCount,
                           String monthlyMonth, int monthlyCount, Instant lastSearchAt, Instant updatedAt) {
        this.id = id;
        this.identifier = identifier;
        this.type = type;
        this.dailyDate = dailyDate;
        this.dailyCount = dailyCount;
        this.monthlyMonth = monthlyMonth;
        this.monthlyCount = monthlyCount;
        this.lastSearchAt = lastSearchAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String identifier;
        private String type;
        private String dailyDate;
        private int dailyCount;
        private String monthlyMonth;
        private int monthlyCount;
        private Instant lastSearchAt;
        private Instant updatedAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder identifier(String identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder dailyDate(String dailyDate) {
            this.dailyDate = dailyDate;
            return this;
        }

        public Builder dailyCount(int dailyCount) {
            this.dailyCount = dailyCount;
            return this;
        }

        public Builder monthlyMonth(String monthlyMonth) {
            this.monthlyMonth = monthlyMonth;
            return this;
        }

        public Builder monthlyCount(int monthlyCount) {
            this.monthlyCount = monthlyCount;
            return this;
        }

        public Builder lastSearchAt(Instant lastSearchAt) {
            this.lastSearchAt = lastSearchAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public SearchRateLimit build() {
            return new SearchRateLimit(id, identifier, type, dailyDate, dailyCount,
                    monthlyMonth, monthlyCount, lastSearchAt, updatedAt);
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDailyDate() {
        return dailyDate;
    }

    public void setDailyDate(String dailyDate) {
        this.dailyDate = dailyDate;
    }

    public int getDailyCount() {
        return dailyCount;
    }

    public void setDailyCount(int dailyCount) {
        this.dailyCount = dailyCount;
    }

    public String getMonthlyMonth() {
        return monthlyMonth;
    }

    public void setMonthlyMonth(String monthlyMonth) {
        this.monthlyMonth = monthlyMonth;
    }

    public int getMonthlyCount() {
        return monthlyCount;
    }

    public void setMonthlyCount(int monthlyCount) {
        this.monthlyCount = monthlyCount;
    }

    public Instant getLastSearchAt() {
        return lastSearchAt;
    }

    public void setLastSearchAt(Instant lastSearchAt) {
        this.lastSearchAt = lastSearchAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
