import { CurrencyPipe } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Category, CategoryService } from '../core/category.service';
import {
    OfferingStatus,
    ProviderOffering,
    ProviderOfferingPage,
    ProviderOfferingRequest,
    ProviderOfferingService,
    ProviderOfferingSort
} from '../core/provider-offering.service';

interface OfferingForm {
    name: string;
    categoryId: string;
    price: number | null;
    shortDescription: string;
    detail: string;
    learningObjectives: string;
    prerequisites: string;
    capacity: number | null;
}

function emptyForm(): OfferingForm {
    return {
        name: '',
        categoryId: '',
        price: null,
        shortDescription: '',
        detail: '',
        learningObjectives: '',
        prerequisites: '',
        capacity: null
    };
}

@Component({
    standalone: true,
    imports: [CurrencyPipe, FormsModule],
    template: `
    <section class="container offerings-page">
      <header class="page-heading">
        <div>
          <p class="eyebrow">PANEL DE PROVEEDOR</p>
          <h1>Mis servicios</h1>
        </div>
        <button class="btn" type="button" (click)="openCreate()" [disabled]="formOpen">Nuevo servicio</button>
      </header>

      @if (success) {
        <p class="message success" role="status">{{ success }}</p>
      }

      @if (actionError) {
        <p class="message error" role="alert">{{ actionError }}</p>
      }

      @if (formOpen) {
        <form class="card form-card" (ngSubmit)="save()" novalidate>
          <h2>{{ editingId ? 'Editar servicio' : 'Nuevo servicio' }}</h2>

          <div class="form-grid">
            <label class="field">
              Nombre *
              <input type="text" name="name" [(ngModel)]="form.name" maxlength="160">
            </label>

            <label class="field">
              Categoría *
              <select name="categoryId" [(ngModel)]="form.categoryId">
                <option value="" disabled>Selecciona una categoría</option>
                @for (category of categoryOptions; track category.id) {
                  <option [value]="category.id">{{ category.name }}</option>
                }
              </select>
            </label>

            <label class="field">
              Precio (COP) *
              <input type="number" name="price" [(ngModel)]="form.price" min="0.01" step="0.01">
            </label>

            <label class="field">
              Cupo
              <input type="number" name="capacity" [(ngModel)]="form.capacity" min="1" step="1">
            </label>
          </div>

          <label class="field">
            Descripción corta
            <textarea name="shortDescription" [(ngModel)]="form.shortDescription" rows="2" maxlength="500"></textarea>
          </label>

          <label class="field">
            Detalle
            <textarea name="detail" [(ngModel)]="form.detail" rows="3"></textarea>
          </label>

          <label class="field">
            Objetivos de aprendizaje
            <textarea name="learningObjectives" [(ngModel)]="form.learningObjectives" rows="3"></textarea>
          </label>

          <label class="field">
            Prerrequisitos
            <textarea name="prerequisites" [(ngModel)]="form.prerequisites" rows="3"></textarea>
          </label>

          @if (formError) {
            <p class="error" role="alert">{{ formError }}</p>
          }

          <div class="form-actions">
            <button class="btn" type="submit" [disabled]="saving">
              {{ saving ? 'Guardando...' : (editingId ? 'Guardar cambios' : 'Crear servicio') }}
            </button>
            <button class="btn secondary" type="button" (click)="closeForm()" [disabled]="saving">Cancelar</button>
          </div>
        </form>
      }

      <section class="filters" aria-label="Orden y tamaño de página">
        <label class="filter-control">
          Ordenar por
          <select [ngModel]="sort" (ngModelChange)="changeSort($event)" [disabled]="loading">
            <option value="NAME_ASC">Nombre A–Z</option>
            <option value="NAME_DESC">Nombre Z–A</option>
            <option value="CREATED_DESC">Más recientes</option>
            <option value="CREATED_ASC">Más antiguos</option>
          </select>
        </label>
        <label class="filter-control">
          Mostrar por página
          <select [ngModel]="pageSize" (ngModelChange)="changePageSize($event)" [disabled]="loading">
            <option [ngValue]="10">10</option>
            <option [ngValue]="25">25</option>
            <option [ngValue]="50">50</option>
          </select>
        </label>
      </section>

      @if (error) {
        <div class="message error" role="alert">
          <span>{{ error }}</span>
          <button class="text-button" type="button" (click)="loadPage()" [disabled]="loading">Reintentar</button>
        </div>
      }

      @if (loading) {
        <p class="message muted" role="status">Cargando servicios...</p>
      } @else if (!error && services.length === 0) {
        <div class="empty-state">
          <h2>Aún no tienes servicios</h2>
          <p class="muted">Cuando crees un servicio, aparecerá aquí.</p>
        </div>
      } @else if (!error) {
        <div class="offering-list" aria-live="polite">
          @for (service of services; track service.id) {
            <article class="offering-row">
              <div class="offering-main">
                <h2>{{ service.name }}</h2>
                <p class="muted">
                  {{ service.code }}
                  @if (categoryName(service.categoryId); as category) { · {{ category }} }
                  @if (service.capacity) { · Cupo: {{ service.capacity }} }
                </p>
                @if (service.shortDescription) {
                  <p class="description">{{ service.shortDescription }}</p>
                }
              </div>
              <span class="status" [class.status-active]="service.status === 'ACTIVE'" [class.status-inactive]="service.status === 'INACTIVE'">
                {{ statusLabel(service.status) }}
              </span>
              <span class="offering-price">{{ service.price | currency:'COP':'symbol-narrow':'1.0-0':'es-CO' }}</span>
              <div class="offering-actions">
                <button class="btn secondary" type="button" (click)="openEdit(service)" [disabled]="saving">Editar</button>
                <button class="btn secondary" type="button" (click)="toggleStatus(service)" [disabled]="saving || togglingId === service.id">
                  {{ togglingId === service.id ? 'Guardando...' : (service.status === 'ACTIVE' ? 'Desactivar' : 'Activar') }}
                </button>
              </div>
            </article>
          }
        </div>

        <footer class="pagination">
          <span class="muted">
            {{ firstVisible }}–{{ lastVisible }} de {{ totalElements }}
          </span>
          <div class="pagination-actions">
            <button class="btn secondary" type="button" (click)="loadPage(currentPage - 1)" [disabled]="loading || currentPage === 0">
              Anterior
            </button>
            <span class="page-number">{{ currentPage + 1 }} / {{ totalPages }}</span>
            <button class="btn secondary" type="button" (click)="loadPage(currentPage + 1)" [disabled]="loading || currentPage + 1 >= totalPages">
              Siguiente
            </button>
          </div>
        </footer>
      }
    </section>
  `,
    styles: [`
    .offerings-page{padding:42px 0 64px;max-width:900px}
    .page-heading{display:flex;align-items:end;justify-content:space-between;gap:20px;margin-bottom:24px}
    .eyebrow{font-size:12px;font-weight:800;letter-spacing:.12em;color:#397367;margin:0 0 8px}
    h1{font-size:30px;margin:0;color:#17332d}
    select{border:1px solid #cfd8e6;border-radius:6px;padding:8px;background:#fff;color:#17332d}
    .form-card{margin-bottom:28px}
    .form-card h2{margin:0 0 16px;font-size:20px;color:#17332d}
    .form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 16px}
    .field select{border-radius:10px;padding:11px}
    .form-actions{display:flex;gap:12px;margin-top:6px}
    .filters{display:flex;flex-wrap:wrap;gap:16px;margin-bottom:22px;padding:14px 0;border-top:1px solid #dfe7e4;border-bottom:1px solid #dfe7e4}
    .filter-control{display:grid;gap:6px;color:#475467;font-size:13px}
    .offering-list{border-top:1px solid #dfe7e4}
    .offering-row{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px 20px;padding:20px 4px;border-bottom:1px solid #dfe7e4;align-items:center}
    .offering-main h2{font-size:17px;margin:0 0 5px;color:#17332d}
    .offering-main p{margin:0 0 4px;font-size:14px}
    .description{color:#344054}
    .status{font-size:12px;font-weight:700;color:#475467;background:#eef2f1;padding:6px 9px;border-radius:4px}
    .status-active{color:#176b4d;background:#e7f4ec}
    .status-inactive{color:#8c3d27;background:#faeee8}
    .offering-price{grid-column:2;color:#17332d;font-size:14px;font-weight:700;text-align:right}
    .offering-actions{grid-column:1 / -1;display:flex;justify-content:flex-end;gap:10px}
    .pagination{display:flex;justify-content:space-between;align-items:center;gap:18px;padding-top:18px;font-size:14px}
    .pagination-actions{display:flex;align-items:center;gap:12px}
    .page-number{min-width:52px;text-align:center;color:#344054}
    .btn{padding:8px 12px;border-radius:6px}
    .btn:disabled{cursor:not-allowed;opacity:.48}
    .empty-state{padding:44px 4px;border-top:1px solid #dfe7e4;border-bottom:1px solid #dfe7e4}
    .empty-state h2{margin:0 0 8px;font-size:20px;color:#17332d}
    .empty-state p{margin:0}
    .message{padding:16px 4px}
    .error{color:#b42318}
    .message.error{display:flex;justify-content:space-between;gap:12px;align-items:center}
    .text-button{border:0;background:none;color:#9f241b;text-decoration:underline;cursor:pointer}
    @media(max-width:600px){
      .offerings-page{padding-top:28px}
      .page-heading{align-items:start;flex-direction:column}
      .form-grid{grid-template-columns:1fr}
      .offering-row{gap:10px}
      .pagination{align-items:start;flex-direction:column}
      .pagination-actions{width:100%;justify-content:space-between}
    }
  `]
})
export class ProviderOfferingsComponent implements OnInit {
    services: ProviderOffering[] = [];
    currentPage = 0;
    pageSize = 10;
    sort: ProviderOfferingSort = 'NAME_ASC';
    totalElements = 0;
    totalPages = 0;
    loading = false;
    error = '';
    success = '';

    categoryOptions: Category[] = [];
    formOpen = false;
    editingId: string | null = null;
    form: OfferingForm = emptyForm();
    saving = false;
    formError = '';
    togglingId: string | null = null;
    actionError = '';

    private categories = new Map<string, string>();

    constructor(
        private offeringService: ProviderOfferingService,
        private categoryService: CategoryService
    ) { }

    get firstVisible(): number {
        return this.totalElements === 0 ? 0 : this.currentPage * this.pageSize + 1;
    }

    get lastVisible(): number {
        return Math.min((this.currentPage + 1) * this.pageSize, this.totalElements);
    }

    ngOnInit(): void {
        this.categoryService.list().subscribe({
            next: list => {
                this.categoryOptions = list;
                this.categories = new Map<string, string>(list.map((c): [string, string] => [c.id, c.name]));
            }
        });
        this.loadPage();
    }

    loadPage(page = this.currentPage): void {
        this.loading = true;
        this.error = '';
        this.offeringService.listMine(page, this.pageSize, this.sort).subscribe({
            next: (result: ProviderOfferingPage) => {
                this.services = result.content;
                this.currentPage = result.page;
                this.totalElements = result.totalElements;
                this.totalPages = result.totalPages;
                this.loading = false;
            },
            error: e => {
                this.error = e?.error?.detail || 'No fue posible cargar tus servicios.';
                this.loading = false;
            }
        });
    }

    changePageSize(size: number): void {
        this.pageSize = Number(size);
        this.loadPage(0);
    }

    changeSort(sort: ProviderOfferingSort): void {
        this.sort = sort;
        this.loadPage(0);
    }

    openCreate(): void {
        this.success = '';
        this.actionError = '';
        this.formError = '';
        this.editingId = null;
        this.form = emptyForm();
        this.formOpen = true;
    }

    openEdit(service: ProviderOffering): void {
        this.success = '';
        this.actionError = '';
        this.formError = '';
        this.editingId = service.id;
        this.form = {
            name: service.name,
            categoryId: service.categoryId,
            price: service.price,
            shortDescription: service.shortDescription ?? '',
            detail: service.detail ?? '',
            learningObjectives: service.learningObjectives ?? '',
            prerequisites: service.prerequisites ?? '',
            capacity: service.capacity
        };
        this.formOpen = true;
    }

    closeForm(): void {
        this.formOpen = false;
        this.editingId = null;
        this.formError = '';
    }

    save(): void {
        this.formError = this.validate();
        if (this.formError) {
            return;
        }

        const request = this.toRequest();
        const editing = this.editingId;
        this.saving = true;
        const call = editing
            ? this.offeringService.update(editing, request)
            : this.offeringService.create(request);

        call.subscribe({
            next: () => {
                this.saving = false;
                this.success = editing ? 'Servicio actualizado correctamente.' : 'Servicio creado correctamente.';
                this.closeForm();
                this.loadPage(editing ? this.currentPage : 0);
            },
            error: e => {
                this.saving = false;
                this.formError = e?.error?.detail || 'No fue posible guardar el servicio.';
            }
        });
    }

    toggleStatus(service: ProviderOffering): void {
        const newStatus: OfferingStatus = service.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
        if (newStatus === 'INACTIVE'
            && !window.confirm(`¿Desactivar "${service.name}"? Dejará de aparecer en el catálogo público.`)) {
            return;
        }

        this.success = '';
        this.actionError = '';
        this.togglingId = service.id;
        this.offeringService.changeStatus(service.id, newStatus).subscribe({
            next: () => {
                this.togglingId = null;
                this.success = newStatus === 'ACTIVE'
                    ? 'Servicio activado. Ya aparece en el catálogo público.'
                    : 'Servicio desactivado. Ya no aparece en el catálogo público.';
                this.loadPage();
            },
            error: e => {
                this.togglingId = null;
                this.actionError = e?.error?.detail || 'No fue posible cambiar el estado del servicio.';
            }
        });
    }

    categoryName(id: string): string {
        return this.categories.get(id) ?? '';
    }

    statusLabel(status: string): string {
        return status === 'ACTIVE' ? 'Activo' : 'Inactivo';
    }

    private validate(): string {
        const f = this.form;
        if (!f.name.trim()) { return 'El nombre es obligatorio.'; }
        if (f.name.trim().length > 160) { return 'El nombre no puede superar 160 caracteres.'; }
        if (!f.categoryId) { return 'Selecciona una categoría.'; }
        if (f.price === null || !(f.price > 0)) { return 'El precio debe ser mayor que cero.'; }
        if (Math.round(f.price * 100) / 100 !== f.price) { return 'El precio admite máximo dos decimales.'; }
        if (f.price > 9999999999.99) { return 'El precio es demasiado alto.'; }
        if (f.shortDescription.trim().length > 500) { return 'La descripción corta no puede superar 500 caracteres.'; }
        if (f.capacity !== null && (!Number.isInteger(f.capacity) || f.capacity < 1)) {
            return 'El cupo debe ser un número entero mayor que cero.';
        }
        return '';
    }

    private toRequest(): ProviderOfferingRequest {
        const f = this.form;
        const clean = (value: string): string | null => value.trim() === '' ? null : value.trim();
        return {
            name: f.name.trim(),
            categoryId: f.categoryId,
            price: f.price as number,
            shortDescription: clean(f.shortDescription),
            detail: clean(f.detail),
            learningObjectives: clean(f.learningObjectives),
            prerequisites: clean(f.prerequisites),
            capacity: f.capacity
        };
    }
}