import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CurrencyPipe, CommonModule } from '@angular/common';
import { Offering, OfferingService } from '../core/offering.service';

import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-service-details',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, RouterModule],
  templateUrl: './service-details.component.html',
  styleUrls: ['./service-details.component.css']
})
export class ServiceDetailsComponent implements OnInit {
  offering: Offering | null = null;
  loading = true;
  error = '';

  constructor(
    private route: ActivatedRoute,
    private service: OfferingService,
    private router: Router
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.service.getById(id).subscribe({
        next: data => {
          this.offering = data;
          this.loading = false;
        },
        error: () => {
          this.error = 'No se pudo cargar el servicio.';
          this.loading = false;
        }
      });
    } else {
      this.error = 'ID de servicio no proporcionado.';
      this.loading = false;
    }
  }

  goBack(): void {
    this.router.navigate(['/']);
  }
}
