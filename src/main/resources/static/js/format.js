(function () {
    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    function formatMoney(value, currency = "RUB") {
        return new Intl.NumberFormat("ru-RU", {
            style: "currency",
            currency
        }).format(value || 0);
    }

    function formatWholeMoney(value) {
        return new Intl.NumberFormat("ru-RU", {
            maximumFractionDigits: 0
        }).format(value || 0) + " ₽";
    }

    function formatDateTime(value) {
        if (!value) {
            return "—";
        }
        return new Intl.DateTimeFormat("ru-RU", {
            dateStyle: "short",
            timeStyle: "short"
        }).format(new Date(value));
    }

    window.escapeHtml = window.escapeHtml || escapeHtml;
    window.formatMoney = window.formatMoney || formatMoney;
    window.formatWholeMoney = window.formatWholeMoney || formatWholeMoney;
    window.formatDateTime = window.formatDateTime || formatDateTime;
})();
