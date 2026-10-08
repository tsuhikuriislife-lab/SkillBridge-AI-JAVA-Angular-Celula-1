# API starter

Base path: `/api`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/auth/register` | Public | Register CUSTOMER and return JWT |
| POST | `/auth/login` | Public | Login and return JWT |
| GET | `/offerings` | Public | Active catalog; Redis-backed |
| GET | `/categories` | Public | Active categories (`id`, `name`) for service forms and catalog labels |
| POST | `/bookings` | Bearer JWT | Persist booking and publish event |
| GET | `/bookings/me?page=0&size=10&sort=DATE_DESC&activity=ALL` | Bearer JWT | Filter and sort the authenticated customer's bookings in pages |
| POST | `/provider/offerings` | PROVIDER | Create a service owned by the authenticated provider |
| PUT | `/provider/offerings/{id}` | PROVIDER | Edit one of the provider's own services |
| PATCH | `/provider/offerings/{id}/status` | PROVIDER | Activate or deactivate one of the provider's own services |
| GET | `/provider/offerings?page=0&size=10&sort=NAME_ASC` | PROVIDER | List the provider's own services in pages |
| POST | `/ai/recommendations` | Bearer JWT | Generate catalog-grounded recommendation |

`GET /bookings/me` defaults to page `0`, size `10`, sort `DATE_DESC`, and activity `ALL`. Page numbering starts at zero; sizes from 1 to 100 are accepted. `sort` accepts `TITLE_ASC`, `TITLE_DESC`, `DATE_ASC`, `DATE_DESC`, `PRICE_ASC`, or `PRICE_DESC`. `activity` accepts `ALL`, `ACTIVE` (created or confirmed), or `INACTIVE` (cancelled or completed). Results include `content`, `page`, `size`, `totalElements`, and `totalPages`; each row includes offering title, price, status, and active flag. The customer is resolved from the JWT, never from a client-supplied ID.

## Public catalog

`GET /offerings` returns only `ACTIVE` services. Each item exposes `id`, `code`, `name`, `categoryId`, `price`, `shortDescription`, `detail`, `learningObjectives`, `prerequisites`, and `capacity`. Provider ownership (`createdBy`) and `status` are never exposed publicly.

## Provider services (HU-10)

All `/provider/**` routes require the `PROVIDER` role. The provider is resolved from the JWT, never from the request body, so a client cannot create or edit services on behalf of someone else.

Request body for `POST` and `PUT`:

| Field | Rules |
|---|---|
| `name` | Required, up to 160 characters |
| `categoryId` | Required, must be an existing `ACTIVE` category |
| `price` | Required, greater than 0, at most 2 decimals, up to 9,999,999,999.99 |
| `shortDescription` | Optional, up to 500 characters |
| `detail`, `learningObjectives`, `prerequisites` | Optional text |
| `capacity` | Optional, at least 1 |

- `POST` creates the service as `ACTIVE` with an automatic code (`SRV-XXXXXXXX`) and responds `201`. `code`, `status` and owner are never taken from the request.
- `PUT` keeps `code`, `status` and owner unchanged.
- `PATCH .../status` takes `{"status": "ACTIVE" | "INACTIVE"}`. Repeating the current status is a no-op.
- `GET /provider/offerings` defaults to page `0`, size `10`, sort `NAME_ASC`. Sizes from 1 to 100 are accepted. `sort` accepts `NAME_ASC`, `NAME_DESC`, `CREATED_ASC`, or `CREATED_DESC`. Results include `content`, `page`, `size`, `totalElements`, and `totalPages`.

| Status | When |
|---|---|
| 400 | Invalid body (blank name, missing price or category, unknown enum value) |
| 403 | The service belongs to another provider ("No puedes modificar un servicio que no es tuyo"), or the caller is not a `PROVIDER` |
| 404 | Service or user not found |
| 422 | Business rule violated (e.g., category does not exist or is inactive, invalid price) |

Create, edit and status changes invalidate the public offerings cache through `OfferingCachePort` after the change is saved, so a deactivated service disappears from `GET /offerings` immediately.

OpenAPI UI locally:

```text
http://localhost:8080/swagger-ui.html
```

## Suggested next endpoints

```text
PATCH  /api/bookings/{id}/cancel
GET    /api/admin/metrics/business
```