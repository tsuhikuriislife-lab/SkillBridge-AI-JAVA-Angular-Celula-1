import { Component, OnInit, AfterViewInit, HostListener, ElementRef } from '@angular/core';
import { CurrencyPipe, LowerCasePipe } from '@angular/common';
import { Router } from '@angular/router';
import { Offering, OfferingService, CategoryCount } from '../core/offering.service';
import { AuthService } from '../core/auth.service';

@Component({
  standalone: true,
  imports: [CurrencyPipe, LowerCasePipe],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit, AfterViewInit {
  offerings: Offering[] = [];
  topCategories: (CategoryCount | null)[] = [];
  error = '';
  showBackToTop = false;
  showLoginToast = false;

  constructor(
    private service: OfferingService, 
    private el: ElementRef,
    private auth: AuthService,
    private router: Router
  ) {}

  private initObserver() {
    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.15 });

    const elements = this.el.nativeElement.querySelectorAll('.reveal:not(.is-visible)');
    elements.forEach((el: any) => observer.observe(el));
  }

  ngOnInit(): void {
    this.service.list().subscribe({
      next: r => {
        this.offerings = r;
        setTimeout(() => this.initObserver(), 50);
      },
      error: () => {
        this.error = 'No fue posible cargar el catálogo.';
        setTimeout(() => this.initObserver(), 50);
      }
    });

    this.service.getTopCategories(8).subscribe({
      next: r => {
        const padded: (CategoryCount | null)[] = [...r];
        while (padded.length < 8) {
          padded.push(null);
        }
        this.topCategories = padded;
        setTimeout(() => this.initObserver(), 50);
      },
      error: () => {
        this.topCategories = Array(8).fill(null);
        setTimeout(() => this.initObserver(), 50);
      }
    });
  }

  ngAfterViewInit(): void {
    this.initObserver();
  }

  @HostListener('window:scroll', [])
  onWindowScroll() {
    this.showBackToTop = window.scrollY > 350;
  }

  scrollToTop(event?: Event) {
    if (event) event.preventDefault();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  scrollToElement(id: string, event: Event) {
    event.preventDefault();
    const element = document.getElementById(id);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  onInscribirse(id: string) {
    if (this.auth.isLoggedIn()) {
      this.router.navigate(['/service', id]);
    } else {
      this.showLoginToast = true;
    }
  }

  goToLogin() {
    this.showLoginToast = false;
    this.router.navigate(['/login']);
  }
}
