import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

/** Tabla notification_log (CA 3.3) en la base de datos PROPIA del microservicio: "notifications". */
@Entity({ name: 'notification_log' })
export class NotificationLogEntity {
  @PrimaryGeneratedColumn('uuid')
  id!: string;

  @Column({ name: 'event_id', type: 'uuid' })
  eventId!: string;

  @Column({ name: 'event_type', type: 'varchar', length: 60 })
  eventType!: string;

  @Column({ type: 'varchar', length: 60 })
  template!: string;

  @Column({ type: 'varchar', length: 254 })
  recipient!: string;

  @Column({
    name: 'provider_message_id',
    type: 'varchar',
    length: 100,
    nullable: true,
    unique: true,
  })
  providerMessageId!: string | null;

  @Column({ type: 'varchar', length: 30 })
  status!: string;

  @Column({ type: 'text', nullable: true })
  error!: string | null;

  @Column({ name: 'last_event_at', type: 'timestamptz' })
  lastEventAt!: Date;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt!: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt!: Date;
}
