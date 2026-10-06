# API starter

Base path: `/api`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/auth/register` | Public | Register CUSTOMER and return JWT |
| POST | `/auth/login` | Public | Login and return JWT |
| GET | `/offerings` | Public | Active catalog; Redis-backed |
| POST | `/bookings` | Bearer JWT | Persist booking and publish event |
| GET | `/bookings/me?page=0&size=10&sort=DATE_DESC&activity=ALL` | Bearer JWT | Filter and sort the authenticated customer's bookings in pages |
| POST | `/ai/recommendations` | Bearer JWT | Generate catalog-grounded recommendation |

`GET /bookings/me` defaults to page `0`, size `10`, sort `DATE_DESC`, and activity `ALL`. Page numbering starts at zero; sizes from 1 to 100 are accepted. `sort` accepts `TITLE_ASC`, `TITLE_DESC`, `DATE_ASC`, `DATE_DESC`, `PRICE_ASC`, or `PRICE_DESC`. `activity` accepts `ALL`, `ACTIVE` (created or confirmed), or `INACTIVE` (cancelled or completed). Results include `content`, `page`, `size`, `totalElements`, and `totalPages`; each row includes offering title, price, status, and active flag. The customer is resolved from the JWT, never from a client-supplied ID.

OpenAPI UI locally:

```text
http://localhost:8080/swagger-ui.html
```

## Suggested next endpoints

```text
POST   /api/provider/offerings
PUT    /api/provider/offerings/{id}
PATCH  /api/provider/offerings/{id}/status
PATCH  /api/bookings/{id}/cancel
GET    /api/admin/metrics/business
```

The provider write endpoints should invalidate the public offerings cache through `OfferingCachePort`.
