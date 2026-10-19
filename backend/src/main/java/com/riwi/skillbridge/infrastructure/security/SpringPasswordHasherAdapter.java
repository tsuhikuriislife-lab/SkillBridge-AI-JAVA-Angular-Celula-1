package com.riwi.skillbridge.infrastructure.security;

import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SpringPasswordHasherAdapter implements PasswordHasherPort {
    private final PasswordEncoder encoder;
    public SpringPasswordHasherAdapter(PasswordEncoder encoder) { this.encoder = encoder; }
    public String encode(String rawPassword) { return encoder.encode(rawPassword); }
    public boolean matches(String rawPassword, String encodedPassword) { return encoder.matches(rawPassword, encodedPassword); }
}
