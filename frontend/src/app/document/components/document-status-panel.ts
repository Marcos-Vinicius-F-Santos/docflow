import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { DocumentResponse, DocumentStatus } from '../document.models';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatCardModule, MatIconModule],
  selector: 'app-document-status-panel',
  styleUrl: './document-status-panel.scss',
  templateUrl: './document-status-panel.html',
})
export class DocumentStatusPanel {
  @Input({ required: true }) document!: DocumentResponse;

  readonly statusSteps: DocumentStatus[] = ['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'];

  readonly statusLabels: Record<DocumentStatus, string> = {
    PENDING: 'PENDING — Recebido',
    PROCESSING: 'PROCESSING — Processando',
    COMPLETED: 'COMPLETED — Concluído',
    FAILED: 'FAILED — Falhou',
  };

  readonly statusDescriptions: Record<DocumentStatus, string> = {
    PENDING: 'O recebimento foi aceito e o processamento assíncrono será iniciado.',
    PROCESSING: 'O armazenamento final ainda está sendo confirmado.',
    COMPLETED: 'O armazenamento final foi confirmado pelo backend.',
    FAILED: 'O processamento falhou e o armazenamento final não foi concluído.',
  };

  readonly statusIcons: Record<DocumentStatus, string> = {
    PENDING: '◷',
    PROCESSING: '⟳',
    COMPLETED: '✓',
    FAILED: '!',
  };

  statusClass(status: DocumentStatus): string {
    return `status-${status.toLowerCase()}`;
  }

  isCurrent(status: DocumentStatus): boolean {
    return this.document.status === status;
  }

  isReached(status: DocumentStatus): boolean {
    if (this.document.status === 'FAILED') {
      return status === 'PENDING' || status === 'PROCESSING' || status === 'FAILED';
    }

    return this.statusSteps.indexOf(status) <= this.statusSteps.indexOf(this.document.status);
  }
}
