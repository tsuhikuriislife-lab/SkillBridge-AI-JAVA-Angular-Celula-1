package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.model.NotificationPage;
import com.riwi.skillbridge.application.port.in.ListMyNotificationsUseCase;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final ListMyNotificationsUseCase listMyNotificationsUseCase;

    public NotificationController(ListMyNotificationsUseCase listMyNotificationsUseCase) {
        this.listMyNotificationsUseCase = listMyNotificationsUseCase;
    }

    @GetMapping("/me")
    public NotificationPage listMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return listMyNotificationsUseCase.list(authentication.getName(), page, size);
    }
}
