import * as Joi from 'joi';

/**
 * Valida TODAS las variables de entorno al arrancar.
 * Si falta una obligatoria o tiene mal formato, el servicio no arranca y dice exactamente qué falla.
 */
export const envValidationSchema = Joi.object({
  NODE_ENV: Joi.string()
    .valid('development', 'production', 'test')
    .default('development'),
  PORT: Joi.number().port().default(3100),

  // Infraestructura
  RABBITMQ_URL: Joi.string()
    .uri({ scheme: ['amqp', 'amqps'] })
    .required(),
  RABBITMQ_PREFETCH: Joi.number().integer().min(1).default(5),
  REDIS_URL: Joi.string()
    .uri({ scheme: ['redis', 'rediss'] })
    .required(),
  IDEMPOTENCY_TTL_SECONDS: Joi.number().integer().min(60).default(604800),
  NOTIFICATIONS_DATABASE_URL: Joi.string()
    .uri({ scheme: ['postgres', 'postgresql'] })
    .required(),

  // Correo (Resend)
  EMAIL_DRIVER: Joi.string().valid('log', 'resend').default('log'),
  RESEND_API_KEY: Joi.when('EMAIL_DRIVER', {
    is: 'resend',
    then: Joi.string().pattern(/^re_/).required(),
    otherwise: Joi.string().allow('').default(''),
  }),
  RESEND_WEBHOOK_SECRET: Joi.string()
    .pattern(/^whsec_/)
    .allow('')
    .default(''),
  MAIL_FROM: Joi.string().default('SkillBridge AI <onboarding@resend.dev>'),
  MAIL_REPLY_TO: Joi.string().allow('').default(''),
  MAIL_REDIRECT_TO: Joi.string().allow('').default(''),
  FRONTEND_URL: Joi.string().uri().default('http://localhost:8088'),
});
