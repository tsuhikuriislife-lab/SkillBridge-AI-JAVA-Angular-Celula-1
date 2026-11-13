import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { NotificationLogPort } from '../../application/ports/notification-log.port';
import { CreateNotificationLog1762880000000 } from './migrations/1762880000000-create-notification-log';
import { NotificationLogEntity } from './notification-log.entity';
import { TypeOrmNotificationLogAdapter } from './typeorm-notification-log.adapter';

@Module({
  imports: [
    TypeOrmModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        type: 'postgres' as const,
        url: config.getOrThrow<string>('NOTIFICATIONS_DATABASE_URL'),
        entities: [NotificationLogEntity],
        migrations: [CreateNotificationLog1762880000000],
        migrationsRun: true, // aplica las migraciones pendientes al arrancar
        synchronize: false, // NUNCA true: el esquema sólo cambia con migraciones
      }),
    }),
    TypeOrmModule.forFeature([NotificationLogEntity]),
  ],
  providers: [
    { provide: NotificationLogPort, useClass: TypeOrmNotificationLogAdapter },
  ],
  exports: [NotificationLogPort],
})
export class PersistenceModule {}
