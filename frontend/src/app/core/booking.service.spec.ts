import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { BookingPage, BookingService } from './booking.service';
import { OfferingService } from './offering.service';

describe('BookingService', () => {
    let service: BookingService;
    let httpTesting: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: OfferingService,
                    useValue: {
                        getById: (id: string) => of({
                            id,
                            title: `Servicio ${id}`,
                            code: `SRV-${id}`,
                            price: 100
                        })
                    }
                }
            ]
        });
        service = TestBed.inject(BookingService);
        httpTesting = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpTesting.verify());

    it('filters active enrollments and maps the readable service name and code', () => {
        const results: BookingPage[] = [];
        service.listMine(0, 10, 'DATE_DESC', 'ACTIVE').subscribe(page => results.push(page));

        httpTesting.expectOne(request => request.url.endsWith('/enrollments/me')).flush({
            content: [
                { serviceId: 'active-service', startDate: '2026-10-14', status: 'ACTIVE' },
                { serviceId: 'cancelled-service', startDate: '2026-10-13', status: 'CANCELLED' }
            ],
            totalPages: 1,
            totalElements: 2,
            number: 0
        });

        expect(results[0].content.length).toBe(1);
        expect(results[0].content[0]).toEqual(jasmine.objectContaining({
            id: 'active-service',
            offeringTitle: 'Servicio active-service',
            serviceCode: 'SRV-active-service',
            status: 'ACTIVE'
        }));
    });

    it('filters inactive enrollments while ALL keeps every status', () => {
        const enrollments = [
            { serviceId: 'active-service', startDate: '2026-10-14', status: 'ACTIVE' },
            { serviceId: 'cancelled-service', startDate: '2026-10-13', status: 'CANCELLED' },
            { serviceId: 'completed-service', startDate: '2026-10-12', status: 'COMPLETED' }
        ];

        const inactiveResults: BookingPage[] = [];
        service.listMine(0, 10, 'DATE_DESC', 'INACTIVE').subscribe(page => inactiveResults.push(page));
        httpTesting.expectOne(request => request.url.endsWith('/enrollments/me')).flush({
            content: enrollments,
            totalPages: 1,
            totalElements: 3,
            number: 0
        });

        expect(inactiveResults[0].content.map(item => item.status))
            .toEqual(['CANCELLED', 'COMPLETED']);

        const allResults: BookingPage[] = [];
        service.listMine(0, 10, 'DATE_DESC', 'ALL').subscribe(page => allResults.push(page));
        httpTesting.expectOne(request => request.url.endsWith('/enrollments/me')).flush({
            content: enrollments,
            totalPages: 1,
            totalElements: 3,
            number: 0
        });

        expect(allResults[0].content.length).toBe(3);
    });
});
