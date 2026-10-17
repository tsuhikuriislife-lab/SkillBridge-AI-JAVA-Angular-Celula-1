package com.riwi.skillbridge.application.port.out;

public interface TokenPort {
    String generate(String email, String role);
}
