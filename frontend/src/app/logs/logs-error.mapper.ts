import { HttpErrorResponse } from '@angular/common/http';

export type ApplicationLogApiErrorKind = 'validation' | 'unavailable' | 'operational';

export interface ApplicationLogApiError {
  kind: ApplicationLogApiErrorKind;
  status?: number;
  message: string;
  canRetryManually: boolean;
}

export function mapApplicationLogError(error: unknown): ApplicationLogApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return operationalError();
  }

  if (error.status === 400) {
    return {
      kind: 'validation',
      status: error.status,
      message: 'Os filtros informados para os logs são inválidos.',
      canRetryManually: false,
    };
  }

  if (error.status === 0 || error.status === 502 || error.status === 503 || error.status === 504) {
    return {
      kind: 'unavailable',
      status: error.status,
      message: 'O serviço de logs está indisponível no momento.',
      canRetryManually: true,
    };
  }

  return operationalError(error.status);
}

function operationalError(status?: number): ApplicationLogApiError {
  return {
    kind: 'operational',
    status,
    message: 'Não foi possível consultar os logs operacionais.',
    canRetryManually: true,
  };
}
