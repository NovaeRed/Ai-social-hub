import { nextTick, watch, type Ref } from 'vue';
export function useDialogFocus(modal: Ref<string>) {
  let lastFocus: HTMLElement | null = null;
  watch(modal, async (value) => {
    if (value) {
      lastFocus = document.activeElement as HTMLElement;
      await nextTick();
      if (modal.value !== value) return;
      document.querySelector<HTMLElement>('.modal input,.modal button')?.focus();
    } else lastFocus?.focus();
  });
  function trapFocus(e: KeyboardEvent) {
    if (e.key !== 'Tab') return;
    const nodes = Array.from(
      document.querySelectorAll<HTMLElement>(
        '.modal button:not(:disabled),.modal input,.modal textarea,.modal select,.modal a[href]',
      ),
    );
    const first = nodes[0],
      last = nodes[nodes.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last?.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first?.focus();
    }
  }

  return { trapFocus };
}
