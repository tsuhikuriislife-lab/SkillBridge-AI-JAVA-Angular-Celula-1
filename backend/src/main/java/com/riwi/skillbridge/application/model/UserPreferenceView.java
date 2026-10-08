// application/model/UserPreferenceView.java
package com.riwi.skillbridge.application.model;

import java.util.UUID;

public record UserPreferenceView(
    UUID preferenceId,
    String name,
    String code,
    String detail,
    boolean assigned          // true = el usuario la tiene ACTIVE
) {}
