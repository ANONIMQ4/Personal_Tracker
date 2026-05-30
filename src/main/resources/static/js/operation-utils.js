(function () {
    let categories = [];
    const categoryColors = new Map();

    function setCategories(nextCategories) {
        categories = Array.isArray(nextCategories) ? nextCategories : [];
        categoryColors.clear();
        categories.forEach((category) => {
            if (category?.name && category?.color) {
                categoryColors.set(category.name, category.color);
            }
        });
    }

    async function loadOperationCategories() {
        const loadedCategories = await apiFetch("/finance/categories");
        setCategories(loadedCategories || []);
        return categories;
    }

    function getCategories() {
        return [...categories];
    }

    function getCategoryColor(categoryName) {
        const normalized = (categoryName || "Без категории").trim();
        return categoryColors.get(normalized) || "#b8c0cc";
    }

    function formatOperationDate(value) {
        if (!value) {
            return "";
        }

        return new Intl.DateTimeFormat("ru-RU", {
            dateStyle: "short",
            timeStyle: "short"
        }).format(new Date(value));
    }

    function normalizeSearchText(value) {
        return String(value ?? "").toLowerCase().replaceAll("ё", "е");
    }

    function getOperationAmount(operation) {
        return Number(operation.operationAmount || 0);
    }

    function getOperationType(operation) {
        return getOperationAmount(operation) < 0 ? "Расход" : "Доход";
    }

    function highlightSearch(value, tokens) {
        const text = escapeHtml(value);
        if (!tokens || tokens.length === 0) {
            return text;
        }

        return tokens.reduce((result, token) => {
            const escapedToken = token.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
            return result.replace(new RegExp(`(${escapedToken})`, "gi"), `<mark class="search-hit">$1</mark>`);
        }, text);
    }

    function csvValue(value) {
        return `"${String(value ?? "").replaceAll('"', '""')}"`;
    }

    function exportOperations(operations, fileName = "operations.csv") {
        const header = ["Дата", "Тип", "Категория", "Описание", "Сумма", "Валюта"];
        const rows = operations.map((operation) => [
            formatOperationDate(operation.operationDate),
            getOperationType(operation),
            operation.category || "Без категории",
            operation.description || "",
            getOperationAmount(operation),
            operation.operationCurrency || "RUB"
        ]);
        const csv = [header, ...rows].map((row) => row.map(csvValue).join(";")).join("\n");
        const blob = new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8" });
        const link = document.createElement("a");
        link.href = URL.createObjectURL(blob);
        link.download = fileName;
        link.click();
        URL.revokeObjectURL(link.href);
    }

    window.OperationUtils = {
        exportOperations,
        formatOperationDate,
        getCategories,
        getCategoryColor,
        getOperationAmount,
        getOperationType,
        highlightSearch,
        loadOperationCategories,
        normalizeSearchText
    };
})();
