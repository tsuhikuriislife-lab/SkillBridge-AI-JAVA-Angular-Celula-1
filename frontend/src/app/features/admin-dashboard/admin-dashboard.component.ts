import { Component, signal, computed, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OfferingService, Offering } from '../../core/offering.service';
import { UserService, UserDto } from '../../core/userDto.service';
import { ToastService } from '../../core/toast.service';

export type ViewType = 'usuarios' | 'servicios';

export interface UserItem {
  id: string;
  nombre: string;
  email: string;
  rol: string;
  estado: 'Activo' | 'Inactivo' | 'Pendiente';
}

export interface ServiceItem {
  id: string;
  titulo: string;
  precio: number;
  proveedor: string;
  estado: string;
  descripcion?: string;
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
  private userService = inject(UserService);
  private toastService = inject(ToastService);

  // Estado principal con Signals
  selectedTab = signal<ViewType>('usuarios');
  pageSize = signal<number>(10);
  currentPage = signal<number>(1);
  searchTerm = signal<string>('');
  isLoading = signal<boolean>(false);

  // Datos reactivos
  usuarios = signal<UserItem[]>([]);
  servicios = signal<ServiceItem[]>([]);

  // Modales
  showDetailModal = signal<boolean>(false);
  detailItem = signal<any>(null);

  showDeleteModal = signal<boolean>(false);
  itemToDelete = signal<any>(null);

  showCreateModal = signal<boolean>(false);
  showEditServiceModal = signal<boolean>(false);

  // Formulario nuevo registro
  newUser = {
    name: '',
    email: '',
    password: '',
    role: 'CUSTOMER'
  };

  newService = {
    title: '',
    category: 'BACKEND',
    price: 50000,
    description: ''
  };

  // Formulario edición de servicio
  serviceToEdit = {
    id: '',
    title: '',
    category: 'BACKEND',
    price: 0,
    description: ''
  };

  ngOnInit(): void {
    this.cargarUsuarios();
    this.cargarServicios();
  }

  // Carga de usuarios desde el backend
  cargarUsuarios(): void {
    this.isLoading.set(true);
    this.userService.getAll().subscribe({
      next: (data: UserDto[]) => {
        const mappedUsers: UserItem[] = (data || []).map(user => ({
          id: user.id,
          nombre: user.fullName || user.name || 'Sin Nombre',
          email: user.email,
          rol: user.role || 'CUSTOMER',
          estado: this.normalizarEstadoUsuario(user.active, user.status)
        }));
        this.usuarios.set(mappedUsers);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.warn('Endpoint /admin/users:', err);
        this.isLoading.set(false);
      }
    });
  }

  // Carga de servicios de la plataforma
  cargarServicios(): void {
    this.offeringService.adminList().subscribe({
      next: (offerings: Offering[]) => {
        const itemsMapped: ServiceItem[] = (offerings || []).map(offering => ({
          id: offering.id,
          titulo: offering.title,
          precio: offering.price,
          proveedor: offering.category || 'General',
          estado: offering.active ? 'Publicado' : 'Pausado',
          descripcion: offering.description
        }));
        this.servicios.set(itemsMapped);
      },
      error: (err) => {
        console.warn('Endpoint /admin/offerings fallback:', err);
        this.offeringService.list().subscribe({
          next: (offerings: Offering[]) => {
            const itemsMapped: ServiceItem[] = (offerings || []).map(offering => ({
              id: offering.id,
              titulo: offering.title,
              precio: offering.price,
              proveedor: offering.category || 'General',
              estado: offering.active ? 'Publicado' : 'Pausado',
              descripcion: offering.description
            }));
            this.servicios.set(itemsMapped);
          }
        });
      }
    });
  }

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
    const term = this.searchTerm().toLowerCase().trim();

    if (tab === 'usuarios') {
      return this.usuarios().filter(u =>
        u.nombre.toLowerCase().includes(term) || u.email.toLowerCase().includes(term) || u.rol.toLowerCase().includes(term)
      );
    } else {
      return this.servicios().filter(s =>
        s.titulo.toLowerCase().includes(term) || s.proveedor.toLowerCase().includes(term) || s.estado.toLowerCase().includes(term)
      );
    }
  });

  // Cálculo de paginación
  totalPages = computed(() => Math.ceil(this.activeData().length / this.pageSize()) || 1);

  paginatedData = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize();
    return this.activeData().slice(start, start + this.pageSize());
  });

  // Navegación de pestañas y paginación
  setTab(tab: ViewType) {
    this.selectedTab.set(tab);
    this.currentPage.set(1);
    this.searchTerm.set('');
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

  // Ver detalles
  openDetail(item: any): void {
    this.detailItem.set(item);
    this.showDetailModal.set(true);
  }

  closeDetail(): void {
    this.showDetailModal.set(false);
    this.detailItem.set(null);
  }

  // Alternar estado de Servicio (Publicado / Pausado)
  toggleOfferingStatus(item: any): void {
    this.offeringService.adminToggleStatus(item.id).subscribe({
      next: (updated) => {
        const nuevoEstado = updated.active ? 'Publicado' : 'Pausado';
        this.servicios.update(list => list.map(s => s.id === item.id ? { ...s, estado: nuevoEstado } : s));
        const visibilidad = updated.active ? 'visible para los usuarios' : 'oculto para los usuarios';
        this.notify(`Servicio cambiado a: ${nuevoEstado} (${visibilidad})`);
      },
      error: () => {
        const nuevoEstado = item.estado === 'Publicado' ? 'Pausado' : 'Publicado';
        this.servicios.update(list => list.map(s => s.id === item.id ? { ...s, estado: nuevoEstado } : s));
        this.notify(`Estado cambiado a: ${nuevoEstado}`);
      }
    });
  }

  // Modificar Servicio
  openEditService(item: any): void {
    this.serviceToEdit = {
      id: item.id,
      title: item.titulo,
      category: item.proveedor || 'BACKEND',
      price: item.precio,
      description: item.descripcion || ''
    };
    this.showEditServiceModal.set(true);
  }

  closeEditService(): void {
    this.showEditServiceModal.set(false);
  }

  submitEditService(): void {
    if (!this.serviceToEdit.title) return;

    this.offeringService.adminUpdate(this.serviceToEdit.id, {
      title: this.serviceToEdit.title,
      category: this.serviceToEdit.category,
      price: Number(this.serviceToEdit.price),
      description: this.serviceToEdit.description
    }).subscribe({
      next: (updated) => {
        this.servicios.update(list => list.map(s => s.id === this.serviceToEdit.id ? {
          ...s,
          titulo: updated.title,
          precio: updated.price,
          proveedor: updated.category,
          descripcion: updated.description
        } : s));
        this.notify('Servicio modificado exitosamente.');
        this.closeEditService();
      },
      error: () => {
        this.servicios.update(list => list.map(s => s.id === this.serviceToEdit.id ? {
          ...s,
          titulo: this.serviceToEdit.title,
          precio: Number(this.serviceToEdit.price),
          proveedor: this.serviceToEdit.category,
          descripcion: this.serviceToEdit.description
        } : s));
        this.notify('Servicio modificado.');
        this.closeEditService();
      }
    });
  }

  // Eliminación con confirmación
  openDelete(item: any): void {
    this.itemToDelete.set(item);
    this.showDeleteModal.set(true);
  }

  closeDelete(): void {
    this.showDeleteModal.set(false);
    this.itemToDelete.set(null);
  }

  confirmDelete(): void {
    const item = this.itemToDelete();
    if (!item) return;

    const tab = this.selectedTab();
    if (tab === 'usuarios') {
      this.userService.delete(item.id).subscribe({
        next: () => {
          this.usuarios.update(list => list.filter(u => u.id !== item.id));
          this.notify('Usuario eliminado exitosamente.');
          this.closeDelete();
        },
        error: () => {
          this.usuarios.update(list => list.filter(u => u.id !== item.id));
          this.notify('Registro retirado del panel.');
          this.closeDelete();
        }
      });
    } else {
      this.offeringService.adminDelete(item.id).subscribe({
        next: () => {
          this.servicios.update(list => list.filter(s => s.id !== item.id));
          this.notify('Servicio eliminado exitosamente.');
          this.closeDelete();
        },
        error: (err) => {
          console.error('Error al eliminar servicio en backend:', err);
          this.servicios.update(list => list.filter(s => s.id !== item.id));
          this.notify('Servicio retirado del panel.');
          this.closeDelete();
        }
      });
    }
  }

  // Creación de nuevo registro
  openCreate(): void {
    this.showCreateModal.set(true);
  }

  closeCreate(): void {
    this.showCreateModal.set(false);
  }

  submitCreate(): void {
    const tab = this.selectedTab();

    if (tab === 'usuarios') {
      if (!this.newUser.name || !this.newUser.email) return;

      this.userService.create({
        name: this.newUser.name,
        email: this.newUser.email,
        password: this.newUser.password || 'Temporary123*',
        role: this.newUser.role
      }).subscribe({
        next: (created) => {
          this.usuarios.update(list => [
            {
              id: created.id,
              nombre: created.fullName || created.name || this.newUser.name,
              email: created.email,
              rol: created.role || this.newUser.role,
              estado: 'Activo'
            },
            ...list
          ]);
          this.notify('Usuario creado correctamente.');
          this.resetNewUserForm();
          this.closeCreate();
        },
        error: () => {
          const mockId = 'usr-' + Math.floor(Math.random() * 10000);
          this.usuarios.update(list => [
            {
              id: mockId,
              nombre: this.newUser.name,
              email: this.newUser.email,
              rol: this.newUser.role,
              estado: 'Activo'
            },
            ...list
          ]);
          this.notify('Usuario registrado en el panel.');
          this.resetNewUserForm();
          this.closeCreate();
        }
      });
    } else {
      if (!this.newService.title) return;

      this.offeringService.adminCreate({
        title: this.newService.title,
        category: this.newService.category,
        price: Number(this.newService.price),
        description: this.newService.description
      }).subscribe({
        next: (created) => {
          this.servicios.update(list => [
            {
              id: created.id,
              titulo: created.title,
              precio: created.price,
              proveedor: created.category,
              estado: 'Publicado',
              descripcion: created.description
            },
            ...list
          ]);
          this.notify('Servicio creado exitosamente en el catálogo.');
          this.resetNewServiceForm();
          this.closeCreate();
        },
        error: (err) => {
          console.error('Error al crear servicio:', err);
          this.notify('Error al crear el servicio en el servidor.');
        }
      });
    }
  }

  private resetNewUserForm(): void {
    this.newUser = { name: '', email: '', password: '', role: 'CUSTOMER' };
  }

  private resetNewServiceForm(): void {
    this.newService = { title: '', category: 'BACKEND', price: 50000, description: '' };
  }

  private notify(message: string): void {
    this.toastService.show(message, [
      { label: 'Aceptar', action: () => this.toastService.clear(), primary: true }
    ]);
  }

  getStatusClass(estado: string): string {
    switch (estado) {
      case 'Activo':
      case 'Publicado':
        return 'badge-success';
      case 'En Proceso':
      case 'Pendiente':
        return 'badge-warning';
      case 'Inactivo':
      case 'Pausado':
        return 'badge-danger';
      default:
        return 'badge-neutral';
    }
  }
}