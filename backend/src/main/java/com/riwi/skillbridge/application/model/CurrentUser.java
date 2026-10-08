package com.riwi.skillbridge.application.model;

import com.riwi.skillbridge.domain.model.Role;

public record CurrentUser(String name, String email, Role role) {
}