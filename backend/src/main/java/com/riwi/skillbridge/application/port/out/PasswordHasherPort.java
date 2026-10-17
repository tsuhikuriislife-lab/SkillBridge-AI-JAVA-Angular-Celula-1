package com.riwi.skillbridge.application.port.out;

public interface PasswordHasherPort {
    String encode(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}
