import { Routes } from '@angular/router';
import { HomeComponent } from './features/home.component';
import { AiComponent } from './features/ai.component';
import { BookingComponent } from './features/booking.component';
import { NotFoundComponent } from './features/not-found.component';
import { authGuard } from './core/auth.guard';
import { MyNotificationsComponent } from './features/my-notifications.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'login', loadComponent: () => import('./features/login/login').then(m => m.LoginComponent) },
  { path: 'ai', component: AiComponent, canActivate: [authGuard] },
  { path: 'book', component: BookingComponent, canActivate: [authGuard] },
  { path: 'bookings', loadComponent: () => import('./features/display-bookings/display-bookings').then(m => m.DisplayBookingsComponent), canActivate: [authGuard] },
  { path: 'notifications', component: MyNotificationsComponent, canActivate: [authGuard] },
  { path: 'service/:id', loadComponent: () => import('./features/service-details.component').then(m => m.ServiceDetailsComponent), canActivate: [authGuard] },
  { path: 'checkout/:id', loadComponent: () => import('./features/checkout.component').then(m => m.CheckoutComponent), canActivate: [authGuard] },
  { 
    path: 'provider', 
    loadComponent: () => import('./features/provider/provider-dashboard.component').then(m => m.ProviderDashboardComponent),
    canActivate: [authGuard],
    children: [
      { path: 'new', loadComponent: () => import('./features/provider/create-offering.component').then(m => m.CreateOfferingComponent) },
      { path: 'services', loadComponent: () => import('./features/provider/my-offerings.component').then(m => m.MyOfferingsComponent) },
      { path: '', redirectTo: 'services', pathMatch: 'full' }
    ]
  },
  { path: '**', component: NotFoundComponent, data: { is404: true } }
];