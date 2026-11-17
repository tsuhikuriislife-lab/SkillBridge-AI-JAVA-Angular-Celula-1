import type { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Migración inmutable (igual que Flyway en el backend): NUNCA se edita una vez mergeada.
 * Cambios futuros = nueva migración con timestamp mayor.
 */
export class CreateNotificationLog1762880000000 implements MigrationInterface {
  name = 'CreateNotificationLog1762880000000';

  async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      CREATE TABLE notification_log (
        id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        event_id            uuid         NOT NULL,
        event_type          varchar(60)  NOT NULL,
        template            varchar(60)  NOT NULL,
        recipient           varchar(254) NOT NULL,
        provider_message_id varchar(100) UNIQUE,
        status              varchar(30)  NOT NULL
          CHECK (status IN ('sent','delivery_delayed','delivered','bounced','complained','failed')),
        error               text,
        last_event_at       timestamptz  NOT NULL DEFAULT now(),
        created_at          timestamptz  NOT NULL DEFAULT now(),
        updated_at          timestamptz  NOT NULL DEFAULT now()
      )
    `);
    await queryRunner.query(
      `CREATE INDEX ix_notification_log_event_id ON notification_log (event_id)`,
    );
    await queryRunner.query(
      `CREATE INDEX ix_notification_log_status ON notification_log (status)`,
    );
  }

  async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE IF EXISTS notification_log`);
  }
}
