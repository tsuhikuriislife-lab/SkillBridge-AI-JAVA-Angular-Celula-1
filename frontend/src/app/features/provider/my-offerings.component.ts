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
      
      @if (loading) { <p>Cargando...</p> }
      @else if (pageData?.content?.length === 0) { <p>No tienes servicios.</p> }
      @else {
        <div class="list">
          @for (o of pageData?.content; track o.id) {
            <div class="card">
              @if (o.photoUrl) { <img [src]="o.photoUrl" class="thumb" /> }
              <div class="info">
                <h3>{{ o.title }} <span class="badge" [class.active]="o.active">{{ o.active ? 'Activo' : 'Inactivo' }}</span></h3>
                <p>{{ o.description }}</p>
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
              <label class="field">Descripción<textarea formControlName="description"></textarea></label>
              <label class="field">Precio<input type="number" formControlName="price" /></label>
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
    .info{flex:1}
    .badge{font-size:12px;padding:2px 8px;border-radius:12px;background:#ccc;color:#000}
    .badge.active{background:#e6f4ea;color:#1e8e3e}
    .actions{display:flex;flex-direction:column;gap:8px}
    .btn.small{padding:6px 12px;font-size:13px}
    .btn.danger{background:#d93025;color:#fff}
    .pagination{display:flex;justify-content:space-between;margin-top:16px;align-items:center}
    .modal{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;z-index:100}
    .modal-content{background:#fff;padding:24px;border-radius:8px;width:100%;max-width:500px}
    .btn.cancel{background:#e7ebf0;color:#333}
  `]
})
export class MyOfferingsComponent implements OnInit {
  offeringService = inject(OfferingService);
  fb = inject(FormBuilder);

  pageData?: PageResult<Offering>;
  page = 0;
  loading = false;

  editItem: Offering | null = null;
  form = this.fb.group({
    title: ['', Validators.required],
    description: ['', Validators.required],
    price: [0, Validators.min(1)]
  });

  toggleItem: Offering | null = null;
  confirmName = '';

  ngOnInit() { this.load(0); }

  load(p: number) {
    this.loading = true;
    this.page = p;
    this.offeringService.listMyServices(this.page, 10).subscribe(res => {
      this.pageData = res;
      this.loading = false;
    });
  }

  openEdit(o: Offering) {
    this.editItem = o;
    this.form.patchValue({
      title: o.title,
      description: o.description,
      price: o.price
    });
  }

  update() {
    if (this.form.invalid || !this.editItem) return;
    this.offeringService.update(this.editItem.id, this.form.value as any).subscribe(() => {
      this.editItem = null;
      this.load(this.page);
    });
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
    this.offeringService.toggleStatus(this.toggleItem.id).subscribe(() => {
      this.toggleItem = null;
      this.load(this.page);
    });
  }
}
