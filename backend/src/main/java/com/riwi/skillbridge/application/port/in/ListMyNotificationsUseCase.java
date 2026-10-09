package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.application.model.NotificationPage;

public interface ListMyNotificationsUseCase {
    NotificationPage list(String customerEmail, int page, int size);
}
