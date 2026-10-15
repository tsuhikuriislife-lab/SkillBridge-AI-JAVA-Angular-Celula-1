import { Routes } from '@angular/router';
import { HomeComponent } from './features/home.component';
import { AiComponent } from './features/ai.component';
import { BookingComponent } from './features/booking.component';
import { NotFoundComponent } from './features/not-found.component';
import { MyNotificationsComponent } from './features/my-notifications.component';
import { authGuard } from './guards/auth.guard';
import { adminGuard } from './guards/admin.guard';
import { nonAdminGuard } from './guards/non-admin.guard';

import { ServiceDetailsComponent } from './features/service-details.component';

export const routes: Routes = [
  { path: '', component: HomeComponent, canActivate: [nonAdminGuard] },
  { path: 'login', loadComponent: () => import('./features/login/login').then(m => m.LoginComponent) },
  { path: 'ai', component: AiComponent, canActivate: [authGuard, nonAdminGuard] },
  { path: 'book', component: BookingComponent, canActivate: [authGuard, nonAdminGuard] },
  { path: 'bookings', loadComponent: () => import('./features/display-bookings/display-bookings').then(m => m.DisplayBookingsComponent), canActivate: [authGuard, nonAdminGuard] },
  { path: 'notifications', component: MyNotificationsComponent, canActivate: [authGuard] },
  { path: 'service/:id', loadComponent: () => import('./features/service-details.component').then(m => m.ServiceDetailsComponent), canActivate: [authGuard, nonAdminGuard] },
  { path: 'admin', loadComponent: () => import('./features/admin-dashboard/admin-dashboard.component').then(m => m.AdminDashboardComponent), canActivate: [adminGuard] },
  { path: 'checkout/:id', loadComponent: () => import('./features/checkout.component').then(m => m.CheckoutComponent), canActivate: [authGuard, nonAdminGuard] },
  { 
    path: 'provider', 
    loadComponent: () => import('./features/provider/provider-dashboard.component').then(m => m.ProviderDashboardComponent),
    canActivate: [authGuard, nonAdminGuard],
    children: [
      { path: 'new', loadComponent: () => import('./features/provider/create-offering.component').then(m => m.CreateOfferingComponent) },
      { path: 'services', loadComponent: () => import('./features/provider/my-offerings.component').then(m => m.MyOfferingsComponent) },
      { path: '', redirectTo: 'services', pathMatch: 'full' }
    ]
  },
  { path: '**', component: NotFoundComponent, data: { is404: true } }
];