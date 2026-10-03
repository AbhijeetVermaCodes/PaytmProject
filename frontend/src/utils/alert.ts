import Swal from 'sweetalert2';

// Base dark theme configuration matching the SeatRush design system
const darkSwal = Swal.mixin({
  background: '#111827',
  color: '#F3F4F6',
  backdrop: 'rgba(3, 7, 18, 0.75)',
  customClass: {
    popup: 'rounded-2xl border border-gray-800 shadow-2xl backdrop-blur-md',
    title: 'text-xl font-extrabold text-white',
    htmlContainer: 'text-sm text-gray-300',
    input: 'bg-gray-900 border border-gray-700 text-white rounded-xl px-3 py-2 text-sm focus:border-purple-500',
    confirmButton: 'px-5 py-2.5 rounded-xl font-bold text-sm bg-gradient-to-r from-blue-600 to-indigo-600 text-white shadow-lg hover:from-blue-500 hover:to-indigo-500 transition-all cursor-pointer mx-1.5',
    cancelButton: 'px-5 py-2.5 rounded-xl font-bold text-sm bg-gray-800 text-gray-300 hover:bg-gray-700 transition-all cursor-pointer mx-1.5',
  },
  buttonsStyling: false,
});

export const showAlert = {
  /**
   * Success modal popup with detailed information and receipt layout
   */
  success: (title: string, message: string, detailsHtml?: string) => {
    return darkSwal.fire({
      icon: 'success',
      iconColor: '#10B981',
      title,
      text: detailsHtml ? undefined : message,
      html: detailsHtml || `<p class="text-sm text-gray-300">${message}</p>`,
      confirmButtonText: 'Great, Continue',
    });
  },

  /**
   * Error / Conflict modal popup with conflicting seat chips
   */
  error: (title: string, message: string, conflictingSeats?: string[]) => {
    let htmlContent = `<p class="text-sm text-gray-300 mb-3">${message}</p>`;
    if (conflictingSeats && conflictingSeats.length > 0) {
      htmlContent += `
        <div class="mt-3 p-3 bg-red-950/50 border border-red-500/40 rounded-xl text-left">
          <span class="text-xs font-bold text-red-300 block mb-1.5">Conflicting / Unavailable Seats:</span>
          <div class="flex flex-wrap gap-1.5">
            ${conflictingSeats
              .map(
                (s) =>
                  `<span class="px-2 py-0.5 rounded bg-red-500/20 text-red-300 font-mono text-xs border border-red-500/30">${s}</span>`
              )
              .join('')}
          </div>
        </div>
      `;
    }

    return darkSwal.fire({
      icon: 'error',
      iconColor: '#EF4444',
      title,
      html: htmlContent,
      confirmButtonText: 'Dismiss',
    });
  },

  /**
   * Interactive confirmation popup for critical actions (e.g. cancel reservation)
   */
  confirm: async (title: string, text: string, confirmText: string = 'Yes, Proceed'): Promise<boolean> => {
    const result = await darkSwal.fire({
      icon: 'warning',
      iconColor: '#F59E0B',
      title,
      text,
      showCancelButton: true,
      confirmButtonText: confirmText,
      cancelButtonText: 'Cancel',
      reverseButtons: true,
    });
    return result.isConfirmed;
  },

  /**
   * Text prompt modal for input (e.g. rejection reasons)
   */
  prompt: async (title: string, text: string, placeholder: string = ''): Promise<string | null> => {
    const result = await darkSwal.fire({
      icon: 'question',
      iconColor: '#8B5CF6',
      title,
      text,
      input: 'text',
      inputPlaceholder: placeholder,
      showCancelButton: true,
      confirmButtonText: 'Submit',
      cancelButtonText: 'Cancel',
      reverseButtons: true,
    });
    if (result.isConfirmed) {
      return result.value || '';
    }
    return null;
  },

  /**
   * Top-right quick toast notification for non-blocking alerts
   */
  toast: (title: string, icon: 'success' | 'error' | 'info' | 'warning' = 'success') => {
    const Toast = Swal.mixin({
      toast: true,
      position: 'top-end',
      showConfirmButton: false,
      timer: 3000,
      timerProgressBar: true,
      background: '#1F2937',
      color: '#F3F4F6',
      customClass: {
        popup: 'rounded-xl border border-gray-700 shadow-xl',
      },
      didOpen: (toast) => {
        toast.onmouseenter = Swal.stopTimer;
        toast.onmouseleave = Swal.resumeTimer;
      },
    });

    return Toast.fire({
      icon,
      title,
    });
  },
};
