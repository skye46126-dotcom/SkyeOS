package com.example.skyeos.data.auth;

public interface CurrentUserContext {
    String requireCurrentUserId();

    String getCurrentUserId();

    void setCurrentUserId(String userId);
}
