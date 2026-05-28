(function () {
    function setMessage(element, message, type = "") {
        if (!element) {
            return;
        }
        element.textContent = message || "";
        element.classList.remove("success", "error");
        if (type) {
            element.classList.add(type);
        }
    }

    function toggleHidden(element, hidden) {
        if (element) {
            element.hidden = hidden;
        }
    }

    function debounce(callback, delay = 160) {
        let timer = null;
        return (...args) => {
            window.clearTimeout(timer);
            timer = window.setTimeout(() => callback(...args), delay);
        };
    }

    window.setStatusMessage = window.setStatusMessage || setMessage;
    window.toggleHidden = window.toggleHidden || toggleHidden;
    window.debounce = window.debounce || debounce;
})();
