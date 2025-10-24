/**
 * Copies the text content from a specified HTML element (e.g., textarea or input)
 * to the user's clipboard.
 * * @param {string} elementId The ID of the element whose content is to be copied.
 */
function copyToClipboard(elementId) {
    const element = document.getElementById(elementId);
    if (!element) {
        console.error('Element with ID ' + elementId + ' not found.');
        return;
    }

    const textToCopy = element.value || element.textContent;

    // 1. Try using the modern Clipboard API
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(textToCopy)
            .then(() => {
                console.log('Text successfully copied (Clipboard API)');
                alert('Contenu copié !');
            })
            .catch(err => {
                console.error('Could not copy text using Clipboard API: ', err);
                // Fallback if API fails or permissions are denied
                fallbackCopyText(element);
            });
    } else {
        // 2. Fallback for older browsers (using document.execCommand)
        fallbackCopyText(element);
    }
}

/**
 * Fallback function using the deprecated document.execCommand('copy').
 * It works well for selecting and copying content from textarea or input fields.
 * * @param {HTMLElement} element The element (e.g., textarea or input) to copy from.
 */
function fallbackCopyText(element) {
    let successful = false;
    try {
        // For textarea/input, select the content directly
        if (element.tagName === 'TEXTAREA' || element.tagName === 'INPUT') {
            element.select();
            // This is required for mobile devices
            element.setSelectionRange(0, 99999);
            successful = document.execCommand('copy');
        } else {
            // For other elements (like DIV), a temporary textarea is needed
            const tempTextArea = document.createElement('textarea');
            tempTextArea.value = element.textContent;

            // Set styles to hide it but keep it selectable
            tempTextArea.style.position = 'fixed';
            tempTextArea.style.top = '0';
            tempTextArea.style.left = '0';
            tempTextArea.style.opacity = '0';

            document.body.appendChild(tempTextArea);
            tempTextArea.focus();
            tempTextArea.select();
            tempTextArea.setSelectionRange(0, 99999);

            successful = document.execCommand('copy');
            document.body.removeChild(tempTextArea);
        }

        if (successful) {
            console.log('Text successfully copied (execCommand fallback)');
            alert('Contenu copié !');
        } else {
            throw new Error('execCommand failed');
        }
    } catch (err) {
        console.error('Fallback: Oops, unable to copy', err);
        alert('Échec de la copie. Veuillez sélectionner le texte manuellement et utiliser Ctrl+C / Cmd+C.');
    }
}

// Ensure the question and response areas are not selected after copying from them
// if execCommand was used, as it leaves the text selected.
// You might want to deselect after the copy operation for a cleaner UI,
// but that is complex to do reliably across all browsers after an async copy.
// For now, the successful copy notification will confirm the action.