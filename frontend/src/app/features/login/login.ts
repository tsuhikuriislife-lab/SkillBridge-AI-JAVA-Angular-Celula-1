import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../core/auth.service'; 

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: 'login.html',
  styleUrl: 'login.css'
})

export class LoginComponent {
  // Manejo de estado
  mode: 'login' | 'register' = 'login';
  showPassword = false;

  // Campos del formulario
  name = '';
  email = '';
  password = '';
  error = '';

  // Textos dinámicos para el encabezado de la SPA
  readonly TEXTS = {
    login: {
      title: 'Aprende tecnología con el servicio ideal',
      description: 'Inicia sesión para explorar servicios de formación tecnológica, recibir recomendaciones con IA y evaluar tus conocimientos.'
    },
    register: {
      title: 'Tu próximo paso en la tecnología empieza aquí',
      description: 'Crea tu cuenta gratis y descubre servicios de formación ofrecidos por terceros, adaptados a tus objetivos.'
    }
  };

  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  constructor() {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed())
      .subscribe(params => {
        const authRequiredMessage = 'Primero debes iniciar sesión para ver esta vista.';
        if (params.get('authRequired') === 'true') {
          this.error = authRequiredMessage;
        } else if (this.error === authRequiredMessage) {
          this.error = '';
        }
      });
  }

  // Propiedad calculada para obtener el título y descripción según el modo actual
  get currentText() {
    return this.TEXTS[this.mode];
  }

  // Cambiar entre vista de inicio de sesión y registro sin manipular el DOM
  toggleMode(newMode: 'login' | 'register') {
    this.mode = newMode;
    this.error = '';
  }

  // Alternar la visibilidad de la contraseña
  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
  }

  // Procesar autenticación o registro
  submit() {
    this.error = '';

    const request$ = this.mode === 'login'
      ? this.auth.login(this.email, this.password)
      : this.auth.register(this.name, this.email, this.password);

    request$.subscribe({
      next: () => {
        this.router.navigateByUrl('/ai');
      },
      error: err => {
        this.error = err?.error?.detail || 'No fue posible realizar la autenticación.';
      }
    });
  }
}