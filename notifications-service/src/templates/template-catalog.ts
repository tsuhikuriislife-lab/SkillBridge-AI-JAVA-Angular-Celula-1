import type { TemplateKey } from '../domain/notification';

/**
 * Asunto y "preheader" (texto gris que Gmail muestra junto al asunto) de cada plantilla.
 * Son plantillas Handlebars de una línea: usan las mismas variables que los .hbs.
 */
export const TEMPLATE_CATALOG: Record<
  TemplateKey,
  { view: string; subject: string; preheader: string }
> = {
  welcome: {
    view: 'welcome',
    subject: '¡Bienvenido/a a SkillBridge AI, {{firstName}}!',
    preheader:
      'Tu cuenta está lista. Explora mentorías y servicios profesionales.',
  },
  'admin.new-registration': {
    view: 'admin-new-registration',
    subject: 'Nuevo usuario registrado: {{subjectName}}',
    preheader: '{{subjectName}} acaba de crear una cuenta.',
  },
  'account-created': {
    view: 'account-created',
    subject: 'Un administrador creó tu cuenta en SkillBridge AI',
    preheader: 'Tu cuenta con rol {{roleLabel}} está lista.',
  },
  'status-changed': {
    view: 'status-changed',
    subject:
      '{{#if deactivated}}Tu cuenta de SkillBridge AI fue desactivada{{else}}Tu cuenta de SkillBridge AI fue reactivada{{/if}}',
    preheader:
      '{{#if deactivated}}Tu cuenta ya no está activa.{{else}}Ya puedes volver a usar la plataforma.{{/if}}',
  },
  'admin.status-changed': {
    view: 'admin-status-changed',
    subject:
      '{{subjectName}} {{#if deactivated}}desactivó{{else}}reactivó{{/if}} su cuenta',
    preheader: 'Cambio de estado de cuenta realizado por el propio usuario.',
  },
  'role-changed': {
    view: 'role-changed',
    subject: 'Tu rol en SkillBridge AI cambió a {{newRoleLabel}}',
    preheader: 'Nuevo rol: {{newRoleLabel}}.',
  },
  'account-deleted': {
    view: 'account-deleted',
    subject: 'Tu cuenta de SkillBridge AI fue eliminada',
    preheader: 'Confirmación de eliminación de cuenta.',
  },
  'admin.account-deleted': {
    view: 'admin-account-deleted',
    subject: '{{subjectName}} eliminó su cuenta',
    preheader: 'Un usuario eliminó su cuenta.',
  },
  'booking-confirmed': {
    view: 'booking-confirmed',
    subject: 'Reserva confirmada: {{serviceName}}',
    preheader: 'Tu inscripción está lista. Inicia el {{startDateLabel}}.',
  },
  'provider.new-enrollment': {
    view: 'provider-new-enrollment',
    subject: 'Nuevo inscrito en {{serviceName}}: {{subjectName}}',
    preheader: '{{subjectName}} se inscribió en tu servicio.',
  },
};
