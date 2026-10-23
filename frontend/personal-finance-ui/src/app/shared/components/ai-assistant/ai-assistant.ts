
import { Component, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

type ChatMode = 'GENERAL' | 'FINANCE';

@Component({
  selector: 'app-ai-assistant',
  standalone: true,
  imports: [MatIconModule],
  templateUrl: './ai-assistant.html',
  styleUrl: './ai-assistant.scss'
})
export class AiAssistant {

  readonly isOpen = signal(false);
  readonly mode = signal<ChatMode>('FINANCE');

  toggleAssistant(): void {
    this.isOpen.update(value => !value);
  }

  closeAssistant(): void {
    this.isOpen.set(false);
  }

  changeMode(mode: ChatMode): void {
    this.mode.set(mode);
  }
}
