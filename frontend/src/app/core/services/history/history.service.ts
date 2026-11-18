import { Injectable } from '@angular/core';
import { Router, NavigationEnd, ActivatedRoute } from '@angular/router';
import { filter } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class HistoryService {
  private validHistory: string[] = [];

  constructor(private router: Router) {
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe((event: any) => {
        const is404 = this.is404Route(this.router.routerState.root);
        if (!is404) {
          const url = event.urlAfterRedirects;
          // Evitamos duplicar la última ruta si recargan o navegan a la misma
          if (this.validHistory[this.validHistory.length - 1] !== url) {
            this.validHistory.push(url);
          }
        }
      });
  }

  private is404Route(route: ActivatedRoute): boolean {
    let current = route;
    while (current.firstChild) {
      current = current.firstChild;
    }
    return current.snapshot.data?.['is404'] === true;
  }

  public goBackToValid() {
    if (this.validHistory.length > 0) {
      const target = this.validHistory[this.validHistory.length - 1];
      this.router.navigateByUrl(target);
    } else {
      this.router.navigate(['/']);
    }
  }
}

