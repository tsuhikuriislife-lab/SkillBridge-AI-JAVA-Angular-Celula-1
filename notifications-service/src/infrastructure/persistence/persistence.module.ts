import { Module } from '@nestjs/common';
import { NotificationLogPort } from '../../application/ports/notification-log.port';
import { InMemoryNotificationLogAdapter } from './in-memory-notification-log.adapter';

/** BASE: registro en memoria. Leonardo (KAN-58) lo cambia por PostgreSQL + TypeORM. */
@Module({
  providers: [
    { provide: NotificationLogPort, useClass: InMemoryNotificationLogAdapter },
  ],
  exports: [NotificationLogPort],
})
export class PersistenceModule {}
