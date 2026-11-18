import 'reflect-metadata';
import { plainToInstance, Type } from 'class-transformer';
import {
  IsArray,
  IsDefined,
  IsEmail,
  IsIn,
  IsInt,
  IsISO8601,
  IsNotEmpty,
  IsObject,
  IsOptional,
  IsString,
  IsUUID,
  ValidateNested,
  validate,
  type ValidationError,
} from 'class-validator';
import {
  NOTIFICATION_EVENT_TYPES,
  type NotificationEvent,
} from '../../domain/notification-event';

const ROLES = ['CUSTOMER', 'PROVIDER', 'ADMIN'];
const STATUSES = ['ACTIVE', 'INACTIVE'];

class ActorDto {
  @IsOptional() @IsUUID('all') id!: string | null;
  @IsEmail() email!: string;
  @IsOptional() @IsIn(ROLES) role!: string | null;
}

class SubjectDto {
  @IsUUID('all') id!: string;
  @IsString() @IsNotEmpty() name!: string;
  @IsEmail() email!: string;
  @IsIn(ROLES) role!: string;
  @IsIn(STATUSES) status!: string;
}

class AdminRecipientDto {
  @IsEmail() email!: string;
  @IsString() name!: string;
}

export class NotificationEventDto {
  @IsUUID('all') eventId!: string;
  @IsIn([...NOTIFICATION_EVENT_TYPES]) eventType!: string;
  @IsInt() @IsIn([1]) version!: number;
  @IsISO8601({ strict: true }) occurredAt!: string;

  @IsOptional()
  @ValidateNested()
  @Type(() => ActorDto)
  actor!: ActorDto | null;

  @IsDefined()
  @ValidateNested()
  @Type(() => SubjectDto)
  subject!: SubjectDto;

  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => AdminRecipientDto)
  admins!: AdminRecipientDto[];

  @IsObject() data!: Record<string, string>;
}

export class InvalidNotificationEventError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'InvalidNotificationEventError';
  }
}

/** Convierte el JSON recibido en un NotificationEvent válido o lanza InvalidNotificationEventError. */
export async function parseNotificationEvent(
  payload: unknown,
): Promise<NotificationEvent> {
  if (
    typeof payload !== 'object' ||
    payload === null ||
    Array.isArray(payload)
  ) {
    throw new InvalidNotificationEventError('El mensaje no es un objeto JSON');
  }

  const dto = plainToInstance(NotificationEventDto, payload);
  const errors = await validate(dto);
  if (errors.length > 0) {
    throw new InvalidNotificationEventError(formatErrors(errors));
  }

  const invalidData = Object.entries(dto.data).find(
    ([, value]) => typeof value !== 'string',
  );
  if (invalidData) {
    throw new InvalidNotificationEventError(
      `data.${invalidData[0]} debe ser string`,
    );
  }

  if (dto.eventType === 'enrollment.created' && !dto.data.serviceName) {
    throw new InvalidNotificationEventError(
      'data.serviceName es obligatorio en enrollment.created',
    );
  }

  return dto as unknown as NotificationEvent;
}

function formatErrors(errors: ValidationError[], parent = ''): string {
  return errors
    .flatMap((error) => {
      const path = parent ? `${parent}.${error.property}` : error.property;
      const own = Object.values(error.constraints ?? {}).map(
        (msg) => `${path}: ${msg}`,
      );
      const nested = error.children?.length
        ? [formatErrors(error.children, path)]
        : [];
      return [...own, ...nested];
    })
    .join('; ');
}
