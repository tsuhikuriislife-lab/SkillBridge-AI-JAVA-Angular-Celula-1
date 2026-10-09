import { Routes } from '@angular/router';
import { HomeComponent } from './features/home/home.component';
import { AiComponent } from './features/ai/ai.component';
import { BookingComponent } from './features/booking.component';
import { NotFoundComponent } from './features/not-found/not-found.component';
import { authGuard } from './core/auth.guard';
import { MyNotificationsComponent } from './features/my-notifications.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'login', loadComponent: () => import('./features/login/login').then(m => m.LoginComponent) },
  { path: 'ai', component: AiComponent, canActivate: [authGuard] },
  { path: 'book', component: BookingComponent, canActivate: [authGuard] },
  { path: 'bookings', loadComponent: () => import('./features/display-bookings/display-bookings').then(m => m.DisplayBookingsComponent), canActivate: [authGuard] },
  { path: 'notifications', component: MyNotificationsComponent, canActivate: [authGuard] },
  { path: 'service/:id', loadComponent: () => import('./features/service-details/service-details.component').then(m => m.ServiceDetailsComponent), canActivate: [authGuard] },
  { path: 'checkout/:id', loadComponent: () => import('./features/checkout/checkout.component').then(m => m.CheckoutComponent), canActivate: [authGuard] },
  { path: '**', component: NotFoundComponent, data: { is404: true } }
];