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
                @for (c of filteredCats; track c) {
                  <li (click)="selectCat(c)">{{ c }}</li>
                }
              </ul>
            }
          </div>
          <small>Seleccionada: <strong>{{ form.value.category || 'Ninguna' }}</strong></small>
        </label>
        
        <label class="field">Precio
          <input type="number" formControlName="price" min="1" />
        </label>

        <div class="row">
          <label class="field">Hora inicio
            <input type="time" formControlName="startTime" />
          </label>
          <label class="field">Hora fin
            <input type="time" formControlName="endTime" />
          </label>
        </div>

        <label class="field">Día finalización (Opcional)
          <input type="text" formControlName="endDay" placeholder="Ej: Viernes" />
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
  
  categories: string[] = [];
  filteredCats: string[] = [];
  searchCat = '';
  showCats = false;
  loading = false;
  msg = '';

  form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(60)]],
    description: ['', [Validators.required, Validators.maxLength(300)]],
    category: ['', Validators.required],
    price: [0, [Validators.required, Validators.min(1)]],
    startTime: ['', Validators.required],
    endTime: ['', Validators.required],
    endDay: [''],
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
    this.filteredCats = this.categories.filter(c => c.toLowerCase().includes(this.searchCat.toLowerCase()));
  }

  selectCat(c: string) {
    this.form.patchValue({ category: c });
    this.showCats = false;
    this.searchCat = '';
  }

  cancel() {
    this.form.reset({ price: 0 });
    this.searchCat = '';
    this.msg = '';
  }

  submit() {
    if (this.form.invalid) return;
    this.loading = true;
    const val = this.form.value as any;
    // ensure seconds
    if (val.startTime && val.startTime.length === 5) val.startTime += ':00';
    if (val.endTime && val.endTime.length === 5) val.endTime += ':00';
    
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
