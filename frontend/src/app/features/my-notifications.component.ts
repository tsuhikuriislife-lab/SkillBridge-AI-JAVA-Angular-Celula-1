import { DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NotificationPage, NotificationService, NotificationSummary } from '../core/notification.service';
import { AuthService } from '../core/auth.service';
import { compactDisplayId } from '../core/display-id';

interface NotificationGroup {
  dateLabel: string;
  items: NotificationSummary[];
}

@Component({
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="container notifications-page">
      <!-- ================= VISTA ADMINISTRADOR ================= -->
      @if (isAdmin) {
        <header class="admin-page-header">
          <div class="header-left">
            <span class="eyebrow">ACTIVIDAD ADMINISTRATIVA</span>
            <h1 class="header-title">Acciones del administrador</h1>
            <p class="header-subtitle">Consulta los cambios realizados desde el panel de administración.</p>
          </div>
          <button class="btn-refresh" type="button" (click)="loadPage()" [disabled]="loading">
            <i class="bx bx-refresh"></i> Actualizar
          </button>
        </header>

        <!-- Tarjetas de resumen -->
        <div class="summary-cards">
          <div class="summary-card">
            <div class="summary-number">{{ totalElements }}</div>
            <div class="summary-label">Acciones registradas</div>
          </div>
          <div class="summary-card">
            <div class="summary-number">{{ countRoleChanges }}</div>
            <div class="summary-label">Cambios de rol</div>
          </div>
          <div class="summary-card">
            <div class="summary-number">{{ countProcessed }} / {{ notifications.length }}</div>
            <div class="summary-label">Procesadas</div>
          </div>
        </div>

        @if (error) {
          <div class="alert-error" role="alert">
            <span>{{ error }}</span>
            <button class="text-button" type="button" (click)="loadPage()" [disabled]="loading">Reintentar</button>
          </div>
        }

        @if (loading) {
          <div class="loading-state">
            <p class="muted">Cargando acciones del administrador...</p>
          </div>
        } @else if (!error && notifications.length === 0) {
          <div class="empty-state">
            <h2>No hay acciones registradas</h2>
            <p class="muted">Las operaciones administrativas realizadas en el panel aparecerán aquí.</p>
          </div>
        } @else if (!error) {
          <div class="activity-feed">
            @for (group of groupedNotifications; track group.dateLabel) {
              <div class="date-group">
                <div class="date-group-label">{{ group.dateLabel }}</div>

                <div class="notification-cards-list">
                  @for (notification of group.items; track notification.id) {
                    <article class="notification-card">
                      <!-- Ícono lateral temático -->
                      <div class="card-icon" [class.icon-role]="isRoleAction(notification)" [class.icon-user]="!isRoleAction(notification)">
                        @if (isRoleAction(notification)) {
                          <i class="bx bx-transfer-alt"></i>
                        } @else {
                          <i class="bx bx-pencil"></i>
                        }
                      </div>

                      <!-- Cuerpo principal -->
                      <div class="card-body">
                        <!-- Fila 1: Título, Código y Estado -->
                        <div class="card-header-row">
                          <div class="card-title-group">
                            <h2 class="card-title">{{ getActionTitle(notification) }}</h2>
                            <span class="action-tag" [class.tag-role]="isRoleAction(notification)" [class.tag-user]="!isRoleAction(notification)">
                              {{ getActionTag(notification) }}
                            </span>
                          </div>

                          <span class="status-badge" [class.status-processed]="notification.status === 'PROCESSED'"
                                [class.status-failed]="notification.status === 'FAILED'">
                            ✓ {{ statusLabel(notification.status) }}
                          </span>
                        </div>

                        <!-- Fila 2: Usuario (Avatar, Correo e ID corto) -->
                        <div class="user-row">
                          <span class="avatar">{{ getAvatarInitial(notification) }}</span>
                          <span class="user-email">{{ getUserEmail(notification) }}</span>
                          <span class="user-id">ID {{ getShortUserId(notification) }}</span>
                        </div>

                        <!-- Fila 3: Cambio de rol o estados -->
                        @if (getPreviousRole(notification) && getNewRole(notification)) {
                          <div class="role-row">
                            <span class="role-pill" [class.role-provider]="getPreviousRole(notification) === 'PROVIDER'"
                                  [class.role-customer]="getPreviousRole(notification) === 'CUSTOMER'"
                                  [class.role-admin]="getPreviousRole(notification) === 'ADMIN'">
                              {{ getPreviousRole(notification) }}
                            </span>
                            <span class="role-arrow">→</span>
                            <span class="role-pill" [class.role-provider]="getNewRole(notification) === 'PROVIDER'"
                                  [class.role-customer]="getNewRole(notification) === 'CUSTOMER'"
                                  [class.role-admin]="getNewRole(notification) === 'ADMIN'">
                              {{ getNewRole(notification) }}
                            </span>

                            @if (getPreviousRole(notification) === getNewRole(notification)) {
                              <span class="role-no-change">Sin cambio de rol</span>
                            }
                          </div>
                        }

                        <!-- Fila 4: Fecha y hora con ícono de reloj -->
                        <div class="time-row">
                          <i class="bx bx-time"></i>
                          <span>{{ formatDateTime(notification.createdAt) }}</span>
                        </div>
                      </div>
                    </article>
                  }
                </div>
              </div>
            }
          </div>

          <!-- Pie de paginación -->
          <footer class="pagination-footer">
            <span class="pagination-info">{{ firstVisible }}–{{ lastVisible }} de {{ totalElements }}</span>
            <div class="pagination-buttons">
              <button class="btn-page" type="button" (click)="loadPage(currentPage - 1)"
                      [disabled]="loading || currentPage === 0">
                Anterior
              </button>
              <span class="page-current">{{ currentPage + 1 }} / {{ totalPages || 1 }}</span>
              <button class="btn-page" type="button" (click)="loadPage(currentPage + 1)"
                      [disabled]="loading || currentPage + 1 >= totalPages">
                Siguiente
              </button>
            </div>
          </footer>
        }
      }

      <!-- ================= VISTA CLIENTE REGULAR ================= -->
      @else {
        <header class="admin-page-header">
          <div class="header-left">
            <span class="eyebrow">TU ACTIVIDAD</span>
            <h1 class="header-title">Mis notificaciones</h1>
            <p class="header-subtitle">Consulta el resultado del procesamiento de las notificaciones de tus reservas.</p>
          </div>
          <button class="btn-refresh" type="button" (click)="loadPage()" [disabled]="loading">
            <i class="bx bx-refresh"></i> Actualizar
          </button>
        </header>

        <!-- Tarjetas de resumen -->
        <div class="summary-cards">
          <div class="summary-card">
            <div class="summary-number">{{ totalElements }}</div>
            <div class="summary-label">Notificaciones</div>
          </div>
          <div class="summary-card">
            <div class="summary-number">{{ countProcessed }} / {{ notifications.length }}</div>
            <div class="summary-label">Procesadas</div>
          </div>
          <div class="summary-card">
            <div class="summary-number">{{ countActiveEnrollments }}</div>
            <div class="summary-label">Inscripciones activas</div>
          </div>
        </div>

        @if (error) {
          <div class="alert-error" role="alert">
            <span>{{ error }}</span>
            <button class="text-button" type="button" (click)="loadPage()" [disabled]="loading">Reintentar</button>
          </div>
        }

        @if (loading) {
          <div class="loading-state">
            <p class="muted">Cargando notificaciones...</p>
          </div>
        } @else if (!error && notifications.length === 0) {
          <div class="empty-state">
            <h2>Aún no tienes notificaciones</h2>
            <p class="muted">Cuando se procese una reserva, su notificación aparecerá aquí.</p>
          </div>
        } @else if (!error) {
          <div class="activity-feed">
            @for (group of groupedNotifications; track group.dateLabel) {
              <div class="date-group">
                <div class="date-group-label">{{ group.dateLabel }}</div>

                <div class="notification-cards-list">
                  @for (notification of group.items; track notification.id) {
                    <article class="notification-card">
                      <!-- Ícono lateral temático -->
                      <div class="client-card-icon">
                        <span class="cap-icon">🎓</span>
                      </div>

                      <!-- Cuerpo principal -->
                      <div class="card-body">
                        <!-- Fila 1: Título de Servicio, Badge de Inscripción y Estado Procesada -->
                        <div class="card-header-row">
                          <div class="card-title-group">
                            <h2 class="card-title">
                              @if (notification.bookingId) {
                                <a [routerLink]="['/service', notification.bookingId]" class="service-link">
                                  {{ notification.title || 'Inscripción a servicio' }}
                                </a>
                              } @else {
                                {{ notification.title || 'Inscripción a servicio' }}
                              }
                            </h2>
                            <span class="badge-inscription">INSCRIPCIÓN</span>
                          </div>

                          <span class="status-badge" [class.status-processed]="notification.status === 'PROCESSED'"
                                [class.status-failed]="notification.status === 'FAILED'">
                            ✓ {{ statusLabel(notification.status) }}
                          </span>
                        </div>

                        <!-- Fila 2: Mensaje y Estado de la inscripción -->
                        <div class="enrollment-status-row">
                          <span class="enrollment-text">El estado de tu inscripción es ahora</span>
                          <span class="enrollment-pill" [class]="getEnrollmentStatusClass(notification)">
                            <span class="status-dot">•</span> {{ getEnrollmentStatus(notification) }}
                          </span>
                        </div>

                        <!-- Fila 3: Fecha y hora con ícono de reloj (última línea) -->
                        <div class="time-row">
                          <i class="bx bx-time"></i>
                          <span>{{ formatDateTime(notification.createdAt) }}</span>
                        </div>
                      </div>
                    </article>
                  }
                </div>
              </div>
            }
          </div>

          <!-- Pie de paginación -->
          <footer class="pagination-footer">
            <span class="pagination-info">{{ firstVisible }}–{{ lastVisible }} de {{ totalElements }}</span>
            <div class="pagination-buttons">
              <button class="btn-page" type="button" (click)="loadPage(currentPage - 1)"
                      [disabled]="loading || currentPage === 0">
                Anterior
              </button>
              <span class="page-current">{{ currentPage + 1 }} / {{ totalPages || 1 }}</span>
              <button class="btn-page" type="button" (click)="loadPage(currentPage + 1)"
                      [disabled]="loading || currentPage + 1 >= totalPages">
                Siguiente
              </button>
            </div>
          </footer>
        }
      }
    </section>
  `,
  styles: [`
    /* ============================================================
       CONTENEDOR GENERAL
       ============================================================ */
    .notifications-page {
      padding: 36px 16px 64px;
      max-width: 920px;
      margin: 0 auto;
    }

    /* ============================================================
       CABECERA ADMINISTRATIVA
       ============================================================ */
    .admin-page-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 20px;
      margin-bottom: 24px;
    }

    .eyebrow {
      display: block;
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.09em;
      color: #0f766e;
      margin-bottom: 6px;
      text-transform: uppercase;
    }

    .header-title {
      font-size: 30px;
      font-weight: 800;
      color: #0f172a;
      letter-spacing: -0.025em;
      margin: 0 0 6px 0;
      line-height: 1.15;
    }

    .header-subtitle {
      font-size: 14px;
      color: #64748b;
      margin: 0;
    }

    .btn-refresh {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      padding: 8px 16px;
      font-size: 13.5px;
      font-weight: 600;
      color: #1e293b;
      cursor: pointer;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
      transition: all 0.15s ease;
      white-space: nowrap;
    }

    .btn-refresh:hover:not(:disabled) {
      background: #f8fafc;
      border-color: #94a3b8;
    }

    .btn-refresh:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .btn-refresh i {
      font-size: 16px;
    }

    /* ============================================================
       TARJETAS DE RESUMEN
       ============================================================ */
    .summary-cards {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 16px;
      margin-bottom: 28px;
    }

    .summary-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 20px 24px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
    }

    .summary-number {
      font-size: 28px;
      font-weight: 800;
      color: #0f172a;
      line-height: 1.1;
      margin-bottom: 4px;
    }

    .summary-label {
      font-size: 13px;
      font-weight: 500;
      color: #64748b;
    }

    /* ============================================================
       GRUPOS DE FECHA
       ============================================================ */
    .date-group {
      margin-bottom: 24px;
    }

    .date-group-label {
      font-size: 11.5px;
      font-weight: 700;
      color: #64748b;
      letter-spacing: 0.06em;
      margin-bottom: 14px;
      text-transform: uppercase;
    }

    .notification-cards-list {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    /* ============================================================
       TARJETA DE NOTIFICACIÓN (ADMIN)
       ============================================================ */
    .notification-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 14px;
      padding: 20px 24px;
      display: flex;
      gap: 18px;
      align-items: flex-start;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
      transition: box-shadow 0.15s ease;
    }

    .notification-card:hover {
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
    }

    /* Ícono temático lateral */
    .card-icon {
      width: 42px;
      height: 42px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
      flex-shrink: 0;
      margin-top: 2px;
    }

    .card-icon.icon-role {
      background: #fef3c7;
      color: #d97706;
    }

    .card-icon.icon-user {
      background: #f1f5f9;
      color: #64748b;
    }

    /* Contenido de la tarjeta */
    .card-body {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 10px;
      min-width: 0;
    }

    /* Fila 1: Título y Estado */
    .card-header-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }

    .card-title-group {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }

    .card-title {
      font-size: 16px;
      font-weight: 700;
      color: #0f172a;
      margin: 0;
    }

    .action-tag {
      font-size: 10.5px;
      font-weight: 700;
      padding: 2px 8px;
      border-radius: 6px;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    .action-tag.tag-role {
      background: #fef3c7;
      color: #b45309;
    }

    .action-tag.tag-user {
      background: #e2e8f0;
      color: #475569;
    }

    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 4px 12px;
      border-radius: 9999px;
      font-size: 12px;
      font-weight: 600;
      white-space: nowrap;
    }

    .status-badge.status-processed {
      background: #dcfce7;
      color: #15803d;
    }

    .status-badge.status-failed {
      background: #fee2e2;
      color: #b91c1c;
    }

    /* Fila 2: Usuario */
    .user-row {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }

    .avatar {
      width: 24px;
      height: 24px;
      border-radius: 50%;
      background: #dbeafe;
      color: #2563eb;
      font-size: 12px;
      font-weight: 700;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      text-transform: uppercase;
    }

    .user-email {
      font-size: 14px;
      font-weight: 700;
      color: #0f172a;
    }

    .user-id {
      background: #f1f5f9;
      color: #64748b;
      font-size: 11px;
      font-weight: 600;
      padding: 2px 6px;
      border-radius: 4px;
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    }

    /* Fila 3: Roles */
    .role-row {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }

    .role-pill {
      font-size: 11px;
      font-weight: 700;
      padding: 4px 10px;
      border-radius: 6px;
      letter-spacing: 0.04em;
    }

    .role-pill.role-provider {
      background: #f3e8ff;
      color: #7e22ce;
    }

    .role-pill.role-customer {
      background: #f1f5f9;
      color: #475569;
    }

    .role-pill.role-admin {
      background: #e0f2fe;
      color: #0369a1;
    }

    .role-arrow {
      color: #64748b;
      font-size: 14px;
      font-weight: 600;
    }

    .role-no-change {
      font-size: 13px;
      font-weight: 500;
      color: #64748b;
      margin-left: 6px;
    }

    /* Fila 4: Timestamp */
    .time-row {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12.5px;
      color: #94a3b8;
      font-weight: 500;
    }

    .time-row i {
      font-size: 14px;
    }

    /* ============================================================
       PAGINACIÓN (ADMIN)
       ============================================================ */
    .pagination-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 20px 0 0;
      font-size: 13.5px;
    }

    .pagination-info {
      color: #64748b;
      font-weight: 500;
    }

    .pagination-buttons {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .btn-page {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 6px 14px;
      font-size: 13px;
      font-weight: 600;
      color: #475569;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .btn-page:hover:not(:disabled) {
      background: #f8fafc;
      border-color: #cbd5e1;
      color: #0f172a;
    }

    .btn-page:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }

    .page-current {
      font-size: 13.5px;
      font-weight: 700;
      color: #0f172a;
      padding: 0 4px;
    }

    /* ============================================================
       ELEMENTOS DE VISTA CLIENTE
       ============================================================ */
    .client-card-icon {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      background: #eff6ff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      flex-shrink: 0;
      margin-top: 2px;
    }

    .cap-icon {
      display: inline-block;
      line-height: 1;
    }

    .badge-inscription {
      background: #dbeafe;
      color: #1d4ed8;
      font-size: 11px;
      font-weight: 700;
      padding: 3px 10px;
      border-radius: 9999px;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    .service-link {
      color: #0f172a;
      text-decoration: none;
      transition: color 0.15s ease;
    }

    .service-link:hover {
      text-decoration: underline;
      color: #2563eb;
    }

    .enrollment-status-row {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 14px;
      color: #334155;
      font-weight: 500;
      flex-wrap: wrap;
    }

    .enrollment-text {
      color: #334155;
    }

    .enrollment-pill {
      font-size: 12px;
      font-weight: 700;
      padding: 3px 10px;
      border-radius: 6px;
      letter-spacing: 0.03em;
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }

    .enrollment-pill.enrollment-active {
      background: #e6f7ed;
      color: #15803d;
    }

    .enrollment-pill.enrollment-cancelled {
      background: #fee2e2;
      color: #b91c1c;
    }

    .enrollment-pill.enrollment-completed {
      background: #e0e7ff;
      color: #4338ca;
    }

    .enrollment-pill.enrollment-pending {
      background: #fef3c7;
      color: #b45309;
    }

    .enrollment-pill.enrollment-default {
      background: #f1f5f9;
      color: #475569;
    }

    .status-dot {
      font-size: 14px;
      line-height: 1;
    }


    .page-number {
      min-width: 52px;
      text-align: center;
      color: #344054;
    }

    .btn {
      padding: 8px 12px;
      border-radius: 6px;
      cursor: pointer;
    }

    .btn.secondary {
      background: #ffffff;
      border: 1px solid #cbd5e1;
      color: #334155;
    }

    .btn:disabled {
      cursor: not-allowed;
      opacity: 0.48;
    }

    .empty-state {
      padding: 44px 4px;
      border-top: 1px solid #dfe7e4;
      border-bottom: 1px solid #dfe7e4;
      text-align: center;
    }

    .empty-state h2 {
      margin: 0 0 8px;
      font-size: 20px;
      color: #17332d;
    }

    .alert-error, .message.error {
      background: #fef2f2;
      border: 1px solid #fecaca;
      border-radius: 8px;
      color: #b91c1c;
      padding: 14px 16px;
      margin-bottom: 20px;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .text-button {
      border: 0;
      background: none;
      color: #9f241b;
      text-decoration: underline;
      cursor: pointer;
      font-weight: 600;
    }

    .loading-state {
      padding: 32px 0;
      text-align: center;
    }

    /* ============================================================
       RESPONSIVE
       ============================================================ */
    @media (max-width: 768px) {
      .admin-page-header {
        flex-direction: column;
        align-items: flex-start;
      }

      .btn-refresh {
        align-self: flex-start;
      }

      .summary-cards {
        grid-template-columns: 1fr;
      }

      .notification-card {
        padding: 16px;
      }
    }

    @media (max-width: 600px) {
      .notifications-page {
        padding-top: 24px;
      }

      .card-header-row {
        flex-direction: column;
        align-items: flex-start;
      }

      .pagination-footer {
        flex-direction: column;
        gap: 14px;
        align-items: flex-start;
      }

      .pagination-buttons {
        width: 100%;
        justify-content: space-between;
      }
    }
  `]
})
export class MyNotificationsComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly pageSize = 20;

  notifications: NotificationSummary[] = [];
  currentPage = 0;
  totalElements = 0;
  totalPages = 0;
  loading = false;
  error = '';

  get isAdmin(): boolean {
    return this.auth.role() === 'ADMIN';
  }

  get countRoleChanges(): number {
    return this.notifications.filter(n => {
      if (n.title === 'ROL MODIFICADO' || n.title === 'USER_ROLE_CHANGED') return true;
      if (n.previousRole && n.newRole && n.previousRole !== n.newRole) return true;
      return false;
    }).length;
  }

  get countProcessed(): number {
    return this.notifications.filter(n => n.status === 'PROCESSED').length;
  }

  get countActiveEnrollments(): number {
    return this.notifications.filter(n => this.getEnrollmentStatus(n) === 'ACTIVE').length;
  }

  get groupedNotifications(): NotificationGroup[] {
    const map = new Map<string, NotificationSummary[]>();

    for (const item of this.notifications) {
      const label = this.formatGroupDate(item.createdAt);
      if (!map.has(label)) {
        map.set(label, []);
      }
      map.get(label)!.push(item);
    }

    return Array.from(map.entries()).map(([dateLabel, items]) => ({
      dateLabel,
      items
    }));
  }

  get firstVisible(): number {
    return this.totalElements === 0 ? 0 : this.currentPage * this.pageSize + 1;
  }

  get lastVisible(): number {
    return Math.min((this.currentPage + 1) * this.pageSize, this.totalElements);
  }

  ngOnInit(): void {
    this.loadPage();
  }

  loadPage(page = this.currentPage): void {
    this.loading = true;
    this.error = '';
    this.notificationService.listMine(page, this.pageSize).subscribe({
      next: (result: NotificationPage) => {
        this.notifications = result.content;
        this.currentPage = result.number !== undefined ? result.number : (result.page ?? 0);
        this.totalElements = result.totalElements;
        this.totalPages = result.totalPages;
        this.loading = false;
      },
      error: () => {
        this.error = 'No fue posible cargar tus notificaciones.';
        this.loading = false;
      }
    });
  }

  formatGroupDate(dateStr: string): string {
    if (!dateStr) return 'ACTIVIDAD';
    const date = new Date(dateStr);
    if (isNaN(date.getTime())) return 'ACTIVIDAD';

    const today = new Date();
    const isToday = date.getFullYear() === today.getFullYear() &&
                    date.getMonth() === today.getMonth() &&
                    date.getDate() === today.getDate();

    const yesterday = new Date();
    yesterday.setDate(today.getDate() - 1);
    const isYesterday = date.getFullYear() === yesterday.getFullYear() &&
                        date.getMonth() === yesterday.getMonth() &&
                        date.getDate() === yesterday.getDate();

    const months = [
      'ENERO', 'FEBRERO', 'MARZO', 'ABRIL', 'MAYO', 'JUNIO',
      'JULIO', 'AGOSTO', 'SEPTIEMBRE', 'OCTUBRE', 'NOVIEMBRE', 'DICIEMBRE'
    ];
    const formatted = `${date.getDate()} DE ${months[date.getMonth()]} DE ${date.getFullYear()}`;
    if (isToday) return `HOY · ${formatted}`;
    if (isYesterday) return `AYER · ${formatted}`;
    return formatted;
  }

  formatDateTime(dateStr: string): string {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    if (isNaN(date.getTime())) return dateStr;

    const day = date.getDate();
    const shortMonths = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
    const month = shortMonths[date.getMonth()];
    const year = date.getFullYear();

    let hours = date.getHours();
    const minutes = date.getMinutes().toString().padStart(2, '0');
    const seconds = date.getSeconds().toString().padStart(2, '0');
    const ampm = hours >= 12 ? 'PM' : 'AM';
    hours = hours % 12;
    hours = hours ? hours : 12;

    return `${day} ${month} ${year} · ${hours}:${minutes}:${seconds} ${ampm}`;
  }

  isRoleAction(notification: NotificationSummary): boolean {
    return notification.title === 'ROL' ||
           notification.title === 'ROL MODIFICADO' ||
           notification.title === 'USER_ROLE_CHANGED' ||
           (!!notification.previousRole && !!notification.newRole && notification.previousRole !== notification.newRole);
  }

  getActionTitle(notification: NotificationSummary): string {
    if (notification.title === 'ROL MODIFICADO' || notification.title === 'USER_ROLE_CHANGED') {
      return 'Rol modificado';
    }
    if (notification.title === 'OFFERING_STATUS_CHANGED') {
      return 'Oferta modificada';
    }
    if (notification.title === 'USER_UPDATED') {
      return 'Usuario actualizado';
    }
    if (notification.title === 'USUARIO CREADO' || notification.title === 'USER_CREATED') {
      return 'Usuario creado';
    }
    if (notification.title === 'USER_DELETED') {
      return 'Usuario eliminado';
    }
    return notification.title || 'Acción administrativa';
  }

  getActionTag(notification: NotificationSummary): string {
    if (notification.title === 'ROL MODIFICADO' || notification.title === 'USER_ROLE_CHANGED') {
      return 'ROL';
    }
    if (notification.title === 'OFFERING_STATUS_CHANGED') {
      return 'Cambio de estado';
    }
    if (notification.title === 'USER_UPDATED') {
      return 'USER_UPDATED';
    }
    if (notification.title === 'USUARIO CREADO' || notification.title === 'USER_CREATED') {
      return 'Nueva cuenta';
    }
    return notification.title || 'ACCIÓN';
  }

  getUserEmail(notification: NotificationSummary): string {
    if (notification.userEmail) return notification.userEmail;
    const match = notification.message?.match(/Correo\s*:\s*([^\\r\\n]+)/);
    if (match) return match[1].trim();
    const matchUpdated = notification.message?.match(/Se actualiz[oó]\s+([^;]+);/);
    if (matchUpdated) return matchUpdated[1].trim();
    return 'usuario@skillbridge.com';
  }

  getShortUserId(notification: NotificationSummary): string {
    const rawId = notification.userId || notification.targetId || '';
    return compactDisplayId(rawId);
  }

  getPreviousRole(notification: NotificationSummary): string | null {
    if (notification.previousRole) return notification.previousRole;
    const match = notification.message?.match(/Rol Anterior\s*:\s*(\\w+)/);
    if (match) return match[1].trim();
    const matchArrow = notification.message?.match(/rol:\s*(\\w+)\s*->\s*(\\w+)/);
    if (matchArrow) return matchArrow[1].trim();
    return null;
  }

  getNewRole(notification: NotificationSummary): string | null {
    if (notification.newRole) return notification.newRole;
    const match = notification.message?.match(/Rol Nuevo\s*:\s*(\\w+)/);
    if (match) return match[1].trim();
    const matchArrow = notification.message?.match(/rol:\s*(\\w+)\s*->\s*(\\w+)/);
    if (matchArrow) return matchArrow[2].trim();
    const matchSingle = notification.message?.match(/Rol\s*:\s*(\\w+)/);
    if (matchSingle) return matchSingle[1].trim();
    return null;
  }

  getAvatarInitial(notification: NotificationSummary): string {
    const email = this.getUserEmail(notification);
    return email ? email.charAt(0).toUpperCase() : 'U';
  }

  statusLabel(status: string): string {
    const labels: Record<string, string> = {
      PENDING: 'Pendiente',
      PROCESSED: 'Procesada',
      FAILED: 'Fallida'
    };
    return labels[status] ?? status;
  }

  getEnrollmentStatus(notification: NotificationSummary): string {
    if (notification.enrollmentStatus) {
      return notification.enrollmentStatus.toUpperCase();
    }
    const match = notification.message?.match(/El estado de tu inscripción es ahora:?\s*(\w+)/i);
    if (match) return match[1].toUpperCase();
    return 'ACTIVE';
  }

  getEnrollmentStatusClass(notification: NotificationSummary): string {
    const status = this.getEnrollmentStatus(notification);
    switch (status) {
      case 'ACTIVE': return 'enrollment-active';
      case 'CANCELLED': return 'enrollment-cancelled';
      case 'COMPLETED': return 'enrollment-completed';
      case 'PENDING': return 'enrollment-pending';
      default: return 'enrollment-default';
    }
  }

  getShortBookingId(notification: NotificationSummary): string {
    return compactDisplayId(notification.bookingId || '');
  }

  formatMessage(message: string): string {
    if (!message) return '';
    let result = message
      .replace(/\bACTIVE\b/g, 'Activa')
      .replace(/\bCANCELLED\b/g, 'Cancelada')
      .replace(/\bCOMPLETED\b/g, 'Completada')
      .replace(/\bPENDING\b/g, 'Pendiente');
    return result.replace(/(El estado de tu inscripción es ahora: [^\n\r]+?)\s+(Reserva:)/i, '$1\n\n$2');
  }
}
