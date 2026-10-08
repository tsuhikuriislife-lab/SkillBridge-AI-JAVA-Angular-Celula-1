import { Component, signal, computed, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OfferingService, Offering } from '../../core/offering.service';
import { UserService, UserDto } from '../../core/userDto.service'; 

export type ViewType = 'usuarios' | 'proveedores' | 'servicios';

export interface UserItem {
  id: string;
  nombre: string;
  email: string;
  rol: string;
  estado: 'Activo' | 'Inactivo' | 'Pendiente';
}

export interface ProviderItem {
  id: string;
  nombreEmpresa: string;
  categoria: string;
  serviciosOfrecidos: number;
  estado: 'Verificado' | 'En Revisión' | 'Suspendido';
}

export interface ServiceItem {
  id: string;
  titulo: string;
  precio: number;
  proveedor: string;
  estado: string;
}

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.css'
})
export class AdminDashboardComponent implements OnInit {
  private offeringService = inject(OfferingService);
  private userService = inject(UserService); // Inyectamos el servicio para usuarios

  // Estado principal con Signals
  selectedTab = signal<ViewType>('usuarios');
  pageSize = signal<number>(10);
  currentPage = signal<number>(1);
  searchTerm = signal<string>('');
  isLoading = signal<boolean>(false);

  // Inicializamos los usuarios con un arreglo vacío
  usuarios = signal<UserItem[]>([]);

  // Datos de proveedores (puedes conectarlos de manera similar cuando tengas su endpoint)
  proveedores = signal<ProviderItem[]>([
    { id: 'prv-201', nombreEmpresa: 'Tech Solutions LLC', categoria: 'Desarrollo Web', serviciosOfrecidos: 12, estado: 'Verificado' },
    { id: 'prv-202', nombreEmpresa: 'Cloud Academy', categoria: 'Capacitación', serviciosOfrecidos: 5, estado: 'Verificado' },
    { id: 'prv-203', nombreEmpresa: 'Data Systems', categoria: 'Infraestructura', serviciosOfrecidos: 2, estado: 'En Revisión' }
  ]);

  servicios = signal<ServiceItem[]>([]);

  ngOnInit(): void {
    this.cargarUsuarios();
    this.cargarServicios();
  }

  // Carga de usuarios desde el backend
  cargarUsuarios(): void {
    this.isLoading.set(true);
    
    // Asumiendo que userService.getAll() o similar devuelve la lista de usuarios
    this.userService.getAll().subscribe({
      next: (data: UserDto[]) => {
        // Mapeamos la respuesta de la API a la interfaz UserItem que usa el dashboard
        const mappedUsers: UserItem[] = data.map(user => ({
          id: user.id,
          nombre: user.fullName || user.name || 'Sin Nombre',
          email: user.email,
          rol: user.role || 'Cliente',
          // Adaptación del estado según lo retorne tu API
          estado: this.normalizarEstadoUsuario(user.active, user.status)
        }));

        this.usuarios.set(mappedUsers);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error('Error al cargar usuarios:', err);
        this.isLoading.set(false);
      }
    });
  }

  // Carga de servicios de la plataforma
  cargarServicios(): void {
    this.offeringService.list().subscribe({
      next: (offerings: Offering[]) => {
        const itemsMapped: ServiceItem[] = offerings.map(offering => ({
          id: offering.id,
          titulo: offering.title,
          precio: offering.price,
          proveedor: offering.category || 'General',
          estado: offering.active ? 'Publicado' : 'Pausado'
        }));

        this.servicios.set(itemsMapped);
      },
      error: (err) => {
        console.error('Error al cargar servicios:', err);
      }
    });
  }

  // Método auxiliar para formatear los estados recibidos del backend
  private normalizarEstadoUsuario(active?: boolean, statusStr?: string): 'Activo' | 'Inactivo' | 'Pendiente' {
    if (typeof active === 'boolean') {
      return active ? 'Activo' : 'Inactivo';
    }
    if (statusStr === 'PENDING' || statusStr === 'Pendiente') return 'Pendiente';
    if (statusStr === 'INACTIVE' || statusStr === 'Inactivo') return 'Inactivo';
    return 'Activo';
  }

  // Selección de dataset según pestaña activa y filtro de búsqueda
  activeData = computed(() => {
    const tab = this.selectedTab();
    const term = this.searchTerm().toLowerCase();

    if (tab === 'usuarios') {
      return this.usuarios().filter(u => 
        u.nombre.toLowerCase().includes(term) || u.email.toLowerCase().includes(term)
      );
    } else if (tab === 'proveedores') {
      return this.proveedores().filter(p => 
        p.nombreEmpresa.toLowerCase().includes(term) || p.categoria.toLowerCase().includes(term)
      );
    } else {
      return this.servicios().filter(s => 
        s.titulo.toLowerCase().includes(term) || s.proveedor.toLowerCase().includes(term)
      );
    }
  });

  // Cálculo de paginación
  totalPages = computed(() => Math.ceil(this.activeData().length / this.pageSize()) || 1);

  paginatedData = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize();
    return this.activeData().slice(start, start + this.pageSize());
  });

  // Métodos de interacción
  setTab(tab: ViewType) {
    this.selectedTab.set(tab);
    this.currentPage.set(1);
  }

  onPageSizeChange(event: Event) {
    const target = event.target as HTMLSelectElement;
    this.pageSize.set(Number(target.value));
    this.currentPage.set(1);
  }

  nextPage() {
    if (this.currentPage() < this.totalPages()) {
      this.currentPage.update(p => p + 1);
    }
  }

  prevPage() {
    if (this.currentPage() > 1) {
      this.currentPage.update(p => p - 1);
    }
  }

  getStatusClass(estado: string): string {
    switch (estado) {
      case 'Activo':
      case 'Verificado':
      case 'Publicado':
        return 'badge-success';
      case 'En Proceso':
      case 'Pendiente':
      case 'En Revisión':
        return 'badge-warning';
      case 'Inactivo':
      case 'Suspendido':
      case 'Pausado':
        return 'badge-danger';
      default:
        return 'badge-neutral';
    }
  }
}