import { Component, inject, OnInit } from '@angular/core';
import { Offering, OfferingService, PageResult } from '../../core/offering.service';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

@Component({
  selector: 'app-my-offerings',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div class="panel">
      <h2>Mis servicios</h2>
      
      @if (msg) { <p class="msg" [class.error]="isError">{{ msg }}</p> }
      
      @if (loading) { <p>Cargando...</p> }
      @else if (pageData?.content?.length === 0) { <p>No tienes servicios.</p> }
      @else {
        <div class="list">
          @for (o of pageData?.content; track o.id) {
            <div class="card">
              @if (o.photoUrl) { <img [src]="o.photoUrl" class="thumb" /> }
              <div class="info">
                <div class="title-row">
                  <h3 [title]="o.title">{{ o.title }}</h3>
                  <span class="badge" [class.active]="o.active">{{ o.active ? 'Activo' : 'Inactivo' }}</span>
                </div>
                <p [title]="o.description">{{ o.description }}</p>
                <strong>\${{ o.price }}</strong>
              </div>
              <div class="actions">
                <button class="btn small" (click)="openEdit(o)">Editar</button>
                <button class="btn small" [class.danger]="o.active" (click)="confirmToggle(o)">
                  {{ o.active ? 'Desactivar servicio' : 'Activar servicio' }}
                </button>
              </div>
            </div>
          }
        </div>
        
        <div class="pagination">
          <button [disabled]="page === 0" (click)="load(page - 1)">Anterior</button>
          <span>Página {{ page + 1 }} de {{ pageData?.totalPages || 1 }}</span>
          <button [disabled]="pageData && page >= pageData.totalPages - 1" (click)="load(page + 1)">Siguiente</button>
        </div>
      }

      @if (editItem) {
        <div class="modal">
          <div class="modal-content">
            <h3>Editar servicio</h3>
            <form [formGroup]="form" (ngSubmit)="update()">
              <label class="field">Nombre<input formControlName="title" /></label>
              <label class="field">Categoría
                <select formControlName="categoryId">
                  @for (c of categories; track c.id) {
                    <option [value]="c.id">{{ c.name }}</option>
                  }
                </select>
              </label>
              <label class="field">Descripción<textarea formControlName="description"></textarea></label>
              <div class="row" style="display:flex;gap:12px">
                <label class="field" style="flex:1">Precio<input type="number" formControlName="price" min="0" /></label>
                <label class="field" style="flex:1">Capacidad<input type="number" formControlName="capacity" min="1" /></label>
              </div>
              <label class="field">URL de Foto (Opcional)<input type="url" formControlName="photoUrl" placeholder="https://..." /></label>
              
              @if (schedules.length > 0) {
                <div class="schedules-section">
                  <h4>Horarios configurados</h4>
                  @for (s of schedules; track s.id) {
                    <div class="schedule-item">
                      <span>{{ s.startDay }} a las {{ s.startTime }} ({{ s.sessionDuration }} min)</span>
                      <button type="button" class="btn small danger" (click)="deleteSchedule(s.id)">X</button>
                    </div>
                  }
                </div>
              }

              <div class="actions">
                <button type="button" class="btn cancel" (click)="editItem = null">Cancelar</button>
                <button type="submit" class="btn">Actualizar</button>
              </div>
            </form>
          </div>
        </div>
      }

      @if (toggleItem) {
        <div class="modal">
          <div class="modal-content">
            <h3>⚠️ Confirmación requerida</h3>
            <p>Escribe exactamente el nombre <strong>{{ toggleItem.title }}</strong> para continuar.</p>
            <input type="text" [value]="confirmName" (input)="setConfirmName($event)" class="field" />
            <div class="actions">
              <button type="button" class="btn cancel" (click)="toggleItem = null">Cancelar</button>
              <button class="btn danger" [disabled]="confirmName !== toggleItem.title" (click)="toggleStatus()">Confirmar</button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .panel{max-width:800px}
    .list{display:flex;flex-direction:column;gap:16px}
    .card{display:flex;gap:16px;background:#fff;padding:16px;border-radius:8px;border:1px solid #e7ebf0;align-items:center}
    .thumb{width:80px;height:80px;object-fit:cover;border-radius:8px;background:#eee}
    .info{flex:1; min-width: 0;}
    .title-row{display: flex; align-items: center; gap: 8px; margin-bottom: 4px;}
    .info h3{white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin: 0; font-size: 1.17em;}
    .info p{white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin: 0 0 8px 0; color: #555;}
    .badge{font-size:12px;padding:2px 8px;border-radius:12px;background:#ccc;color:#000; flex-shrink: 0;}
    .badge.active{background:#e6f4ea;color:#1e8e3e}
    .actions{display:flex;flex-direction:column;gap:8px}
    .btn.small{padding:6px 12px;font-size:13px}
    .btn.danger{background:#d93025;color:#fff}
    .pagination{display:flex;justify-content:space-between;margin-top:16px;align-items:center}
    .modal{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;z-index:100}
    .modal-content{background:#fff;padding:24px;border-radius:8px;width:100%;max-width:500px}
    .field{display:block;margin-bottom:12px}
    .field input, .field textarea{width:100%;padding:8px;border:1px solid #ccc;border-radius:4px;margin-top:4px}
    .btn.cancel{background:#e7ebf0;color:#333}
    .msg{margin-bottom:16px;color:green}
    .msg.error{color:#d93025;background:#fce8e6;padding:10px;border-radius:4px;border:1px solid #f8cdcd;}
    .field input.ng-invalid.ng-touched,
    .field textarea.ng-invalid.ng-touched,
    .field select.ng-invalid.ng-touched {
      border-color: #d93025;
      outline: 1px solid #d93025;
    }
    .schedules-section { margin-top: 16px; padding-top: 16px; border-top: 1px solid #e7ebf0; }
    .schedules-section h4 { margin: 0 0 8px 0; font-size: 14px; color: #555; }
    .schedule-item { display: flex; justify-content: space-between; align-items: center; padding: 6px 12px; background: #f9f9f9; border-radius: 4px; margin-bottom: 8px; font-size: 14px; }
  `]
})
export class MyOfferingsComponent implements OnInit {
  offeringService = inject(OfferingService);
  fb = inject(FormBuilder);

  pageData?: PageResult<Offering>;
  page = 0;
  loading = false;
  msg = '';
  isError = false;

  categories: any[] = [];
  editItem: Offering | null = null;
  schedules: any[] = [];
  
  form = this.fb.group({
    title: ['', Validators.required],
    description: ['', Validators.required],
    price: [0, Validators.min(0)],
    categoryId: ['', Validators.required],
    photoUrl: [''],
    capacity: [10, [Validators.required, Validators.min(1)]]
  });

  toggleItem: Offering | null = null;
  confirmName = '';

  ngOnInit() { 
    this.load(0); 
    this.offeringService.getCategories().subscribe(res => {
      this.categories = res;
    });
  }

  load(p: number) {
    this.loading = true;
    this.page = p;
    this.offeringService.listMyServices(this.page, 10).subscribe({
      next: res => {
        this.pageData = res;
        this.loading = false;
      },
      error: err => {
        this.loading = false;
        this.showMessage(err.error?.detail || err.error?.message || 'Error al cargar servicios.', true);
      }
    });
  }

  showMessage(message: string, isError: boolean) {
    this.msg = message;
    this.isError = isError;
    setTimeout(() => this.msg = '', 5000);
  }

  openEdit(o: Offering) {
    this.editItem = o;
    this.schedules = [];
    this.form.patchValue({
      title: o.title,
      description: o.description,
      price: o.price,
      categoryId: o.categoryId,
      photoUrl: o.photoUrl === 'images/placeholder.jpg' ? '' : o.photoUrl,
      capacity: o.capacity || 10
    });
    this.offeringService.getSchedules(o.id).subscribe({
      next: res => this.schedules = res,
      error: err => console.error("Error cargando horarios", err)
    });
  }

  update() {
    if (this.form.invalid || !this.editItem) {
      this.form.markAllAsTouched();
      this.showMessage('Por favor, completa todos los campos correctamente.', true);
      return;
    }
    const updates = { 
      ...this.form.value, 
      code: this.editItem.code
    } as any;
    this.offeringService.update(this.editItem.id, updates, this.editItem.active).subscribe({
      next: () => {
        this.editItem = null;
        this.showMessage('Servicio actualizado exitosamente.', false);
        this.load(this.page);
      },
      error: err => {
        this.showMessage(err.error?.detail || err.error?.message || 'Error al actualizar servicio.', true);
      }
    });
  }

  deleteSchedule(scheduleId: string) {
    if (!this.editItem) return;
    if (confirm('¿Estás seguro de que deseas eliminar este horario?')) {
      this.offeringService.deleteSchedule(this.editItem.id, scheduleId).subscribe({
        next: () => {
          this.schedules = this.schedules.filter(s => s.id !== scheduleId);
          this.showMessage('Horario eliminado exitosamente.', false);
        },
        error: err => {
          this.showMessage(err.error?.detail || err.error?.message || 'Error al eliminar horario.', true);
        }
      });
    }
  }

  confirmToggle(o: Offering) {
    this.toggleItem = o;
    this.confirmName = '';
  }

  setConfirmName(e: Event) {
    this.confirmName = (e.target as HTMLInputElement).value;
  }

  toggleStatus() {
    if (!this.toggleItem) return;
    this.offeringService.toggleStatus(this.toggleItem.id, this.toggleItem.active).subscribe({
      next: () => {
        this.toggleItem = null;
        this.showMessage('Estado actualizado exitosamente.', false);
        this.load(this.page);
      },
      error: err => {
        this.showMessage(err.error?.detail || err.error?.message || 'Error al actualizar el estado.', true);
      }
    });
  }
}
