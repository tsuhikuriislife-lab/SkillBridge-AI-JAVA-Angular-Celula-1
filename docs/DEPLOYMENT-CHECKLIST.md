# Deployment Checklist

## Before deploying
- [ ] Tests pass.
- [ ] No secrets are committed.
- [ ] `JWT_SECRET` is long and random.
- [ ] Production database uses TLS.
- [ ] Redis URL uses TLS when the provider requires it.
- [ ] RabbitMQ cloud URL uses the credentials supplied by the provider.
- [ ] `GEMINI_API_KEY` exists only in backend environment variables.
- [ ] `CORS_ALLOWED_ORIGINS` contains only the real frontend URL(s).
- [ ] `/actuator/health` returns UP.
- [ ] Swagger is reviewed; consider disabling it for a real production product.

## After deploying
- [ ] Register/login works.
- [ ] Public catalog works twice (second request should be cache-eligible).
- [ ] Authenticated AI endpoint works.
- [ ] Create booking works.
- [ ] RabbitMQ queue is consumed.
- [ ] Logs contain no passwords/tokens/API keys.
- [ ] Metrics are visible locally with the observability profile.
