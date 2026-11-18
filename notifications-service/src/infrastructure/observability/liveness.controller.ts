import { Controller, Get } from '@nestjs/common';

/** Liveness mínimo para el healthcheck de Docker: "el proceso responde". */
@Controller('health')
export class LivenessController {
  @Get('live')
  live(): { status: string } {
    return { status: 'ok' };
  }
}
