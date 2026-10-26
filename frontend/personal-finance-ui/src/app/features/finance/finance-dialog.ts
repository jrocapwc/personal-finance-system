import { Component, ElementRef, input, output, ViewChild } from '@angular/core';
@Component({
  selector: 'app-finance-dialog',
  standalone: true,
  template: `<dialog
    #dialog
    [attr.aria-labelledby]="dialogId() + '-title'"
    (cancel)="cancel($event)"
    (close)="closed.emit()"
  >
    <header>
      <h2 [id]="dialogId() + '-title'">{{ title() }}</h2>
      <button
        type="button"
        class="close"
        aria-label="Close dialog"
        [disabled]="busy()"
        (click)="close()"
      >
        &#215;
      </button>
    </header>
    <ng-content />
  </dialog>`,
  styleUrl: './finance-dialog.scss',
})
export class FinanceDialog {
  readonly title = input.required<string>();
  readonly dialogId = input.required<string>();
  readonly busy = input(false);
  readonly closed = output<void>();
  @ViewChild('dialog', { static: true }) element!: ElementRef<HTMLDialogElement>;
  open(): void {
    if (!this.element.nativeElement.open) this.element.nativeElement.showModal();
  }
  close(): void {
    if (this.element.nativeElement.open) this.element.nativeElement.close();
  }
  cancel(event: Event): void {
    if (this.busy()) event.preventDefault();
  }
}
