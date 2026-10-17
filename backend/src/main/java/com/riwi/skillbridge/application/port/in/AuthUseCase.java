package com.riwi.skillbridge.application.port.in;

public interface AuthUseCase {
    String register(String name, String email, String rawPassword);
    String login(String email, String rawPassword);
}
