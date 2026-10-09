package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.AdminActivityUseCase;
import com.riwi.skillbridge.domain.model.AdminActivity;
import com.riwi.skillbridge.domain.model.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminNotificationController {
    private final AdminActivityUseCase adminActivity;

    public AdminNotificationController(AdminActivityUseCase adminActivity) {
        this.adminActivity = adminActivity;
    }

    @GetMapping
    public PageResult<AdminNotificationOut> listMine(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<AdminActivity> result = adminActivity.listMine(authentication.getName(), page, size);
        List<AdminNotificationOut> content = result.content().stream()
                .map(activity -> new AdminNotificationOut(activity.id().toString(), "ADMIN_ACTIVITY",
                        activity.message(), "PROCESSED", activity.createdAt().toString(), null,
                        activity.action(), activity.targetId() == null ? null : activity.targetId().toString(),
                        activity.targetType()))
                .toList();
        return new PageResult<>(content, result.totalPages(), result.totalElements(), result.number());
    }

    public record AdminNotificationOut(
            String id,
            String type,
            String message,
            String status,
            String createdAt,
            String readAt,
            String title,
            String targetId,
            String targetType
    ) {}
}
