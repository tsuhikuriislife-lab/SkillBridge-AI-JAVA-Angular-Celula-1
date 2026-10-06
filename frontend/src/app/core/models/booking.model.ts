export type BookingStatus = 'CREATED' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'Activo' | 'En Proceso' | 'Cancelado' | 'Completado';

export interface Booking {
  id: string;
  offeringId?: string;
  customerId?: string;
  scheduledAt?: string;
  status: BookingStatus;
  
  // Propiedades enriquecidas para la vista (si el backend o el servicio las mapean)
  serviceTitle?: string;
  totalSessions?: number;
  category?: string;
  progress?: number;
  iconUrl?: string;
}