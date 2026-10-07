import { Routes } from '@angular/router';
import { HomeComponent } from './features/home.component';
import { AiComponent } from './features/ai.component';
import { BookingComponent } from './features/booking.component';
import { NotFoundComponent } from './features/not-found.component';
import { authGuard } from './core/auth.guard';

import { ServiceDetailsComponent } from './features/service-details.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'login', loadComponent: () => import('./features/login/login').then(m => m.LoginComponent) },
  { path: 'ai', component: AiComponent, canActivate: [authGuard] },
  { path: 'book', component: BookingComponent, canActivate: [authGuard] },
  { path: 'bookings', loadComponent: () => import('./features/my-booking.component').then(m => m.MyBookingsComponent), canActivate: [authGuard] },
  { path: 'service/:id', loadComponent: () => import('./features/service-details.component').then(m => m.ServiceDetailsComponent), canActivate: [authGuard] },
  { path: '**', component: NotFoundComponent, data: { is404: true } }
];
