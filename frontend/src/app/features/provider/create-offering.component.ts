import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { OfferingService } from '../../core/offering.service';

@Component({
  selector: 'app-create-offering',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div class="panel">
      <h2>Agregar nuevo servicio</h2>
      <form [formGroup]="form" (ngSubmit)="submit()">
        <label class="field">
          Nombre del servicio (máx 60 letras)
          <input formControlName="title" maxlength="60" placeholder="Ej: Mentoría Backend" />
        </label>
        
        <label class="field">
          Descripción (máx 300 letras)
          <textarea formControlName="description" rows="3" maxlength="300" placeholder="Describe tu servicio..."></textarea>
        </label>
        
        <label class="field">Categoría
          <div class="custom-select">
            <input type="text" placeholder="Buscar categoría..." [value]="searchCat" (input)="filterCats($event)" (focus)="showCats=true" />
            @if (showCats) {
              <ul class="cat-list">
                @for (c of filteredCats; track c.id) {
                  <li (click)="selectCat(c)">{{ c.name }}</li>
                }
              </ul>
            }
          </div>
          <small>Seleccionada: <strong>{{ selectedCategoryName || 'Ninguna' }}</strong></small>
        </label>
        
        <label class="field">Precio
          <input type="number" formControlName="price" min="0" step="0.01" />
        </label>

        <div class="row">
          <label class="field">Fecha de Inicio
            <input type="date" formControlName="startDate" />
          </label>
          <label class="field">Día de la semana
            <select formControlName="startDay">
              <option value="">Seleccione un día</option>
              <option value="MONDAY">Lunes</option>
              <option value="TUESDAY">Martes</option>
              <option value="WEDNESDAY">Miércoles</option>
              <option value="THURSDAY">Jueves</option>
              <option value="FRIDAY">Viernes</option>
              <option value="SATURDAY">Sábado</option>
              <option value="SUNDAY">Domingo</option>
            </select>
          </label>
        </div>

        <div class="row">
          <label class="field">Duración (minutos)
            <input type="number" formControlName="sessionDuration" min="1" />
          </label>
          <label class="field">Frecuencia
            <select formControlName="frequency">
              <option value="WEEKLY">Semanal</option>
              <option value="BIWEEKLY">Quincenal</option>
              <option value="MONTHLY">Mensual</option>
            </select>
          </label>
        </div>

        <label class="field">Cantidad de sesiones
          <input type="number" formControlName="numberOfSessions" min="1" />
        </label>

        <label class="field">URL de Foto (Opcional)
          <input type="url" formControlName="photoUrl" placeholder="https://..." />
        </label>

        <div class="actions">
          <button type="button" class="btn cancel" (click)="cancel()">Cancelar</button>
          <button type="submit" class="btn" [disabled]="form.invalid || loading">{{ loading ? 'Guardando...' : 'Crear servicio' }}</button>
        </div>
      </form>
      @if (msg) { <p class="msg">{{ msg }}</p> }
    </div>
  `,
  styles: [`
    .panel{max-width:600px;background:#fff;padding:24px;border-radius:8px;border:1px solid #e7ebf0}
    .row{display:flex;gap:12px}
    .row > label {flex:1}
    .field{display:block;margin-bottom:12px}
    .field input, .field textarea, .field select{width:100%;padding:8px;border:1px solid #ccc;border-radius:4px;margin-top:4px}
    .custom-select{position:relative}
    .cat-list{position:absolute;top:100%;left:0;right:0;max-height:200px;overflow-y:auto;background:#fff;border:1px solid #ccc;list-style:none;padding:0;margin:0;z-index:10}
    .cat-list li{padding:8px 12px;cursor:pointer}
    .cat-list li:hover{background:#f3f6fb}
    .actions{display:flex;gap:12px;margin-top:16px}
    .btn.cancel{background:#e7ebf0;color:#333}
    .msg{margin-top:16px;color:green}
  `]
})
export class CreateOfferingComponent implements OnInit {
  fb = inject(FormBuilder);
  offeringService = inject(OfferingService);
  
  categories: any[] = [];
  filteredCats: any[] = [];
  searchCat = '';
  showCats = false;
  selectedCategoryName = '';
  loading = false;
  msg = '';

  form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(60)]],
    description: ['', [Validators.required, Validators.maxLength(300)]],
    category: ['', Validators.required],
    price: [0, [Validators.required, Validators.min(0)]],
    startDate: ['', Validators.required],
    startDay: ['', Validators.required],
    sessionDuration: [60, [Validators.required, Validators.min(1)]],
    frequency: ['WEEKLY', Validators.required],
    numberOfSessions: [1, [Validators.required, Validators.min(1)]],
    photoUrl: ['']
  });

  ngOnInit() {
    this.offeringService.getCategories().subscribe(res => {
      this.categories = res;
      this.filteredCats = res;
    });
    document.addEventListener('click', (e: any) => {
      if (!e.target.closest('.custom-select')) this.showCats = false;
    });
  }

  filterCats(e: Event) {
    this.searchCat = (e.target as HTMLInputElement).value;
    this.filteredCats = this.categories.filter(c => c.name.toLowerCase().includes(this.searchCat.toLowerCase()));
  }

  selectCat(c: any) {
    this.form.patchValue({ category: c.id });
    this.selectedCategoryName = c.name;
    this.showCats = false;
    this.searchCat = '';
  }

  cancel() {
    this.form.reset({ price: 0, sessionDuration: 60, frequency: 'WEEKLY', numberOfSessions: 1 });
    this.searchCat = '';
    this.selectedCategoryName = '';
    this.msg = '';
  }

  submit() {
    if (this.form.invalid) return;
    this.loading = true;
    const val = this.form.value as any;
    
    this.offeringService.create(val).subscribe({
      next: () => {
        this.msg = 'Servicio creado exitosamente!';
        this.cancel();
        this.loading = false;
        setTimeout(() => this.msg='', 3000);
      },
      error: () => {
        this.msg = 'Error al crear servicio.';
        this.loading = false;
      }
    });
  }
}
