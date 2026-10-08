package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.application.port.in.ProcessBookingNotificationUseCase;
import com.riwi.skillbridge.infrastructure.adapter.out.messaging.BookingCreatedEvent;
import com.riwi.skillbridge.infrastructure.config.RabbitConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BookingNotificationConsumer {
    private static final Logger log = LoggerFactory.getLogger(BookingNotificationConsumer.class);
    private final ProcessBookingNotificationUseCase processNotificationUseCase;
    private final MeterRegistry meterRegistry;

    public BookingNotificationConsumer(
            ProcessBookingNotificationUseCase processNotificationUseCase,
            MeterRegistry meterRegistry
    ) {
        this.processNotificationUseCase = processNotificationUseCase;
        this.meterRegistry = meterRegistry;
    }

    @RabbitListener(queues = RabbitConfiguration.BOOKING_CREATED_QUEUE)
    public void onBookingCreated(BookingCreatedEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            boolean inserted = processNotificationUseCase.processBookingCreated(event.bookingId());
            String metric = inserted ? "processed" : "duplicate";
            meterRegistry.counter("skillbridge.notifications." + metric).increment();
        } catch (RuntimeException exception) {
            meterRegistry.counter("skillbridge.notifications.failed_attempts").increment();
            log.error(
                    "Booking notification processing failed; eventType=BOOKING_CREATED, errorType={}",
                    exception.getClass().getSimpleName()
            );
            throw exception;
        } finally {
            sample.stop(meterRegistry.timer("skillbridge.notifications.processing"));
        }
    }
}
