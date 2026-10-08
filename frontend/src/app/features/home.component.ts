import { Component, OnInit } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { Offering, OfferingService } from '../core/offering.service';
import { CategoryService } from '../core/category.service';

@Component({
  standalone: true,
  imports: [CurrencyPipe],
  template: `
    <section class="hero"><div class="container"><p class="eyebrow">PROYECTO INTEGRADOR</p><h1>Servicios, eventos, caché e IA en una sola solución.</h1><p>Base profesional para practicar arquitectura hexagonal, Angular, Spring Boot y cloud.</p></div></section>
    <section class="container section">
      <h2>Servicios disponibles</h2>
      @if (error) { <p class="error">{{ error }}</p> }
      <div class="grid">
        @for (offering of offerings; track offering.id) {
          <article class="card">
            @if (categoryName(offering.categoryId); as category) { <small>{{ category }}</small> }
            <h3>{{ offering.name }}</h3>
            <p class="muted">{{ offering.shortDescription }}</p>
            <strong>{{ offering.price | currency:'COP':'symbol-narrow':'1.0-0' }}</strong>
          </article>
        }
      </div>
    </section>
  `,
  styles: [`.hero{padding:70px 0;background:linear-gradient(135deg,#101c35,#27426f);color:white}.hero h1{font-size:clamp(2rem,5vw,4rem);max-width:850px;margin:.3rem 0}.eyebrow{font-weight:800;letter-spacing:.12em}.section{padding:42px 0}`]
})
export class HomeComponent implements OnInit {
  offerings: Offering[] = [];
  error = '';
  private categories = new Map<string, string>();

  constructor(private service: OfferingService, private categoryService: CategoryService) {}

  ngOnInit(): void {
    this.service.list().subscribe({
      next: r => this.offerings = r,
      error: () => this.error = 'No fue posible cargar el catálogo.'
    });
    this.categoryService.list().subscribe({
      next: list => this.categories = new Map(list.map(c => [c.id, c.name]))
    });
  }

  categoryName(id: string): string {
    return this.categories.get(id) ?? '';
  }
}