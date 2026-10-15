package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.AdminActivityUseCase;
import com.riwi.skillbridge.application.port.out.AdminActivityRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.AdminActivity;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AdminActivityService implements AdminActivityUseCase {
    private final AdminActivityRepositoryPort activityRepository;
    private final UserAccountPort userAccountPort;

    public AdminActivityService(AdminActivityRepositoryPort activityRepository, UserAccountPort userAccountPort) {
        this.activityRepository = activityRepository;
        this.userAccountPort = userAccountPort;
    }

    @Override
    public void record(String actorEmail, String action, String targetType, UUID targetId, String message) {
        UserAccount actor = requireAdmin(actorEmail);
        activityRepository.save(new AdminActivity(UUID.randomUUID(), actor.id(), action, targetType,
                targetId, message, OffsetDateTime.now()));
    }

    @Override
    public PageResult<AdminActivity> listMine(String actorEmail, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessRuleException("La página no puede ser negativa y el tamaño debe estar entre 1 y 100");
        }
        UserAccount actor = requireAdmin(actorEmail);
        return activityRepository.findPageByActorId(actor.id(), page, size);
    }

    private UserAccount requireAdmin(String email) {
        UserAccount user = userAccountPort.findByEmail(email)
                .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));
        if (user.role() != Role.ADMIN) {
            throw new BusinessRuleException("Solo un administrador puede acceder a esta actividad");
        }
        return user;
    }
}
