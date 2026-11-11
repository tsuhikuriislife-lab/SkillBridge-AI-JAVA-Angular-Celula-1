import { Module } from '@nestjs/common';
import { LivenessController } from './liveness.controller';

/** BASE: sólo /health/live. Leonardo (KAN-58) agrega /health completo con Terminus. */
@Module({
  controllers: [LivenessController],
})
export class ObservabilityModule {}
