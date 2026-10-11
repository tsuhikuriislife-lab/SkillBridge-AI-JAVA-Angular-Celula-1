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
        
        <div class="category-container">
          <label class="field half">Categoría
            <div class="custom-select" [class.error-box]="form.get('category')?.invalid && form.get('category')?.touched">
              <input type="text" placeholder="Buscar categoría..." [value]="searchCat" (input)="filterCats($event)" (focus)="showCats=true" />
              @if (showCats) {
                <ul class="cat-list">
                  @for (c of filteredCats; track c.id) {
                    <li (click)="selectCat(c)">{{ c.name }}</li>
                  }
                </ul>
              }
            </div>
          </label>
          <div class="half selected-category-display">
            Categoría seleccionada:<br>
            <strong>{{ selectedCategoryName || 'Ninguna' }}</strong>
          </div>
        </div>
        
        <label class="field">Precio
          <input type="number" formControlName="price" min="0" step="0.01" />
        </label>

        <div class="row">
          <label class="field">Hora inicio
            <input type="time" formControlName="startTime" />
          </label>
        </div>

        <div class="row">
          <label class="field">Fecha de Inicio
            <input type="date" formControlName="startDate" max="2099-12-31" />
          </label>
          <label class="field">Días de la semana
            <div class="days-checkboxes" [class.error-box]="form.get('startDays')?.invalid && form.get('startDays')?.touched">
              <label><input type="checkbox" [checked]="selectedDays.includes('MONDAY')" (change)="toggleDay('MONDAY')"> Lun</label>
              <label><input type="checkbox" [checked]="selectedDays.includes('TUESDAY')" (change)="toggleDay('TUESDAY')"> Mar</label>
              <label><input type="checkbox" [checked]="selectedDays.includes('WEDNESDAY')" (change)="toggleDay('WEDNESDAY')"> Mié</label>
              <label><input type="checkbox" [checked]="selectedDays.includes('THURSDAY')" (change)="toggleDay('THURSDAY')"> Jue</label>
              <label><input type="checkbox" [checked]="selectedDays.includes('FRIDAY')" (change)="toggleDay('FRIDAY')"> Vie</label>
              <label><input type="checkbox" [checked]="selectedDays.includes('SATURDAY')" (change)="toggleDay('SATURDAY')"> Sáb</label>
              <label><input type="checkbox" [checked]="selectedDays.includes('SUNDAY')" (change)="toggleDay('SUNDAY')"> Dom</label>
            </div>
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
          <button type="submit" class="btn" [disabled]="loading">{{ loading ? 'Guardando...' : 'Crear servicio' }}</button>
        </div>
      </form>
      @if (msg) { <p class="msg" [class.error]="isError">{{ msg }}</p> }
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
    .msg.error{color:#d93025;background:#fce8e6;padding:10px;border-radius:4px;border:1px solid #f8cdcd;}
    .category-container { display: flex; gap: 16px; align-items: flex-end; margin-bottom: 12px; }
    .half { flex: 1; margin-bottom: 0 !important; }
    .selected-category-display { padding: 8px 12px; background: #f3f6fb; border-radius: 4px; border: 1px solid #ccc; font-size: 14px; height: 37px; }
    .days-checkboxes { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; padding: 4px; }
    .days-checkboxes label { display: flex; align-items: center; gap: 4px; font-size: 14px; }
    .field input.ng-invalid.ng-touched,
    .field textarea.ng-invalid.ng-touched,
    .field select.ng-invalid.ng-touched {
      border-color: #d93025;
      outline: 1px solid #d93025;
    }
    .error-box {
      border: 1px solid #d93025 !important;
      border-radius: 4px;
    }
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
  isError = false;

  selectedDays: string[] = [];

  form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(60)]],
    description: ['', [Validators.required, Validators.maxLength(300)]],
    category: ['', Validators.required],
    price: [0, [Validators.required, Validators.min(0)]],
    startTime: ['', Validators.required],
    startDate: ['', [Validators.required, this.dateValidator()]],
    startDays: [[] as string[], Validators.required],
    sessionDuration: [60, [Validators.required, Validators.min(1)]],
    frequency: ['WEEKLY', Validators.required],
    numberOfSessions: [1, [Validators.required, Validators.min(1)]],
    photoUrl: ['']
  });

  dateValidator() {
    return (control: any) => {
      if (!control.value) return null;
      const inputDate = new Date(control.value + 'T00:00:00');
      
      // Check max date
      if (inputDate.getFullYear() > 2099) {
          return { maxDate: true };
      }

      const minDate = new Date();
      minDate.setDate(minDate.getDate() + 2);
      minDate.setHours(0, 0, 0, 0);
      
      if (inputDate < minDate) {
        return { minDate: true };
      }
      return null;
    };
  }

  toggleDay(day: string) {
    if (this.selectedDays.includes(day)) {
      this.selectedDays = this.selectedDays.filter(d => d !== day);
    } else {
      this.selectedDays.push(day);
    }
    this.form.patchValue({ startDays: this.selectedDays });
  }

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
    this.form.reset({ price: 0, sessionDuration: 60, frequency: 'WEEKLY', numberOfSessions: 1, startDays: [] });
    this.searchCat = '';
    this.selectedCategoryName = '';
    this.selectedDays = [];
    this.msg = '';
    this.isError = false;
  }

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.isError = true;
      if (this.form.get('startDate')?.hasError('minDate')) {
        this.msg = 'La fecha de inicio debe tener al menos 48 horas de anticipación.';
      } else if (this.form.get('startDate')?.hasError('maxDate')) {
        this.msg = 'El año de la fecha de inicio es demasiado lejano. Por favor, ingresa un año válido.';
      } else {
        this.msg = 'Por favor, completa todos los campos obligatorios correctamente.';
      }
      return;
    }
    
    this.loading = true;
    this.msg = '';
    this.isError = false;
    const val = this.form.value as any;
    
    this.offeringService.create(val).subscribe({
      next: () => {
        this.cancel();
        this.isError = false;
        this.msg = 'Servicio creado exitosamente!';
        this.loading = false;
        setTimeout(() => this.msg='', 5000);
      },
      error: (err) => {
        this.isError = true;
        this.msg = err.error?.detail || err.error?.message || 'Error al crear servicio.';
        this.loading = false;
      }
    });
  }
}
