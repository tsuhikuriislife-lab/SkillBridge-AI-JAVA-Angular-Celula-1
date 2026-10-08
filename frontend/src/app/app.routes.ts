import { Routes } from '@angular/router';
import { HomeComponent } from './features/home.component';
import { LoginComponent } from './features/login.component';
import { AiComponent } from './features/ai.component';
import { BookingComponent } from './features/booking.component';
import { MyBookingsComponent } from './features/my-bookings.component';
import { MyNotificationsComponent } from './features/my-notifications.component';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'login', component: LoginComponent },
  { path: 'ai', component: AiComponent, canActivate: [authGuard] },
  { path: 'book', component: BookingComponent, canActivate: [authGuard] },
  { path: 'bookings', component: MyBookingsComponent, canActivate: [authGuard] },
  { path: 'notifications', component: MyNotificationsComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: '' }
];
