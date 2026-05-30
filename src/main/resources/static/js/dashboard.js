const title = document.querySelector("#title");
    const content = document.querySelector("#content");
    const logoutButton = document.querySelector("#logout");
    const uploadForm = document.querySelector("#upload-form");
    const uploadMessage = document.querySelector("#upload-message");
    const financeFile = document.querySelector("#finance-file");
    const fileDropZone = document.querySelector("#file-drop-zone");
    const fileDropSubtitle = document.querySelector("#file-drop-subtitle");
    const financePanels = document.querySelectorAll(".finance-panel");
    const manualForm = document.querySelector("#manual-form");
    const manualMessage = document.querySelector("#manual-message");
    const manualType = document.querySelector("#manual-type");
    const manualCategory = document.querySelector("#manual-category");
    const operationsBody = document.querySelector("#operations");
    const selectAll = document.querySelector("#select-all");
    const operationSearch = document.querySelector("#operation-search");
    const sortMode = document.querySelector("#sort-mode");
    const operationTypeFilter = document.querySelector("#operation-type-filter");
    const categoryFilter = document.querySelector("#category-filter");
    const transferPersonControl = document.querySelector("#transfer-person-control");
    const transferPersonFilter = document.querySelector("#transfer-person-filter");
    const periodTabs = document.querySelector("#period-tabs");
    const customPeriodForm = document.querySelector("#custom-period-form");
    const deleteSelectedButton = document.querySelector("#delete-selected");
    const deleteMessage = document.querySelector("#delete-message");
    const income = document.querySelector("#income");
    const expenses = document.querySelector("#expenses");
    const balance = document.querySelector("#balance");
    const chartTotal = document.querySelector("#chart-total");
    const donutWrap = document.querySelector("#donut-wrap");
    const donut = document.querySelector("#donut");
    const categoryPills = document.querySelector("#category-pills");
    const dashboardPeriod = document.querySelector("#dashboard-period");
    const dashboardTopCategory = document.querySelector("#dashboard-top-category");
    const dashboardTopCategoryNote = document.querySelector("#dashboard-top-category-note");
    const customChartForm = document.querySelector("#custom-chart-form");
    const customChartCategories = document.querySelector("#custom-chart-categories");
    const customChartClearCategories = document.querySelector("#custom-chart-clear-categories");
    const customChartSelectCategories = document.querySelector("#custom-chart-select-categories");
    const customChartMetric = document.querySelector("#custom-chart-metric");
    const customChartType = document.querySelector("#custom-chart-type");
    const chartTypeButtons = document.querySelectorAll(".chart-type-button");
    const customChartAverage = document.querySelector("#custom-chart-average");
    const customChartSummary = document.querySelector("#custom-chart-summary");
    const customChartCanvas = document.querySelector("#custom-chart-canvas");
    const emptyOperationsState = document.querySelector("#empty-operations-state");
    const dashboardContent = document.querySelector("#dashboard-content");
    const emptyImportButton = document.querySelector("#empty-import-button");
    const emptyManualButton = document.querySelector("#empty-manual-button");
    let allOperations = [];
    let searchQuery = "";
    let searchTimer = null;
    let activeEditor = null;
    const operationFeedback = new Map();
    let categoryOptions = [];
    let selectedPeriod = { type: "all", key: null, from: null, to: null };
    let periodWasInitialized = false;
    let donutMode = "expense";
    let currentAnalytics = null;
    let analyticsLoadToken = 0;
    const chartColors = ["#8fb7ff", "#9fd7f0", "#88d9cd", "#c7b4f7", "#f5bd84", "#eba4cf", "#b8c0cc", "#9aa8bb"];
    const categoryIcons = {
        "Переводы": "↔",
        "Супермаркеты": "🛒",
        "Медицина": "+",
        "Цифровые товары": "▣",
        "Связь": "◌",
        "Ж/д билеты": "▸",
        "Остальное": "…"
    };

    function getCategoryColor(categoryName) {
        const normalized = (categoryName || "Без категории").trim();
        return categoryOptions.find((category) => category.name === normalized)?.color || "#B8C0CC";
    }

    function getDonutCategoryColor(categoryName, index, mode) {
        const expensePalette = ["#8fb7ff", "#9fd7f0", "#c7b4f7", "#f5bd84", "#eba4cf", "#b8c0cc"];
        const incomePalette = ["#8bd8b0", "#88d9cd", "#c7dc82", "#9fd7f0", "#94d7b9", "#b8c0cc"];
        const fallbackPalette = mode === "income" ? incomePalette : expensePalette;

        return getCategoryColor(categoryName) || fallbackPalette[index % fallbackPalette.length];
    }

    function formatDate(value) {
        if (!value) {
            return "";
        }

        return new Intl.DateTimeFormat("ru-RU", {
            dateStyle: "medium",
            timeStyle: "short"
        }).format(new Date(value));
    }

    async function loadAccount() {
        if (!title || !content) {
            return;
        }

        let user;
        try {
            user = await apiFetch("/me");
        } catch (error) {
            content.textContent = "Не удалось загрузить аккаунт";
            return;
        }

        title.textContent = `Мой аккаунт: ${user.username}`;
        content.className = "";
        content.innerHTML = `
            <dl>
                <dt>ID</dt>
                <dd>${user.id}</dd>
                <dt>Имя</dt>
                <dd>${user.username}</dd>
                <dt>Email</dt>
                <dd>${user.email}</dd>
                <dt>Создан</dt>
                <dd>${formatDate(user.createdAt)}</dd>
            </dl>
        `;
    }

    function updateEmptyState() {
        const hasOperations = Array.isArray(allOperations) && allOperations.length > 0;
        if (emptyOperationsState) {
            emptyOperationsState.hidden = hasOperations;
        }
        if (dashboardContent) {
            dashboardContent.hidden = !hasOperations;
        }
    }

    function openOperationsAction(action) {
        sessionStorage.setItem("operations:openDrawer", action);
        window.location.href = "/operations";
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

    function formatChartValue(value, metric) {
        if (metric === "count") {
            return new Intl.NumberFormat("ru-RU").format(value || 0);
        }
        return formatWholeMoney(value);
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

    function getPeriodLabel() {
        if (selectedPeriod.type === "all") {
            return "Все время";
        }
        if (selectedPeriod.type === "year") {
            return `${selectedPeriod.year} год`;
        }
        if (selectedPeriod.type === "month") {
            return getMonthLabel(selectedPeriod.key);
        }
        if (selectedPeriod.type === "custom" && selectedPeriod.from && selectedPeriod.to) {
            const formatter = new Intl.DateTimeFormat("ru-RU", { dateStyle: "medium" });
            return `${formatter.format(selectedPeriod.from)} — ${formatter.format(selectedPeriod.to)}`;
        }
        return "Текущий период";
    }

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    function normalizeSearchText(value) {
        return String(value ?? "").toLowerCase().replaceAll("ё", "е");
    }

    function getOperationType(operation) {
        return getOperationAmount(operation) < 0 ? "Расход" : "Доход";
    }

    function getSearchTokens() {
        return normalizeSearchText(searchQuery).split(/\s+/).filter(Boolean);
    }

    function highlightSearch(value) {
        const text = escapeHtml(value);
        const tokens = getSearchTokens();
        if (tokens.length === 0) {
            return text;
        }

        return tokens.reduce((result, token) => {
            const escapedToken = token.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
            return result.replace(new RegExp(`(${escapedToken})`, "gi"), `<mark class="search-hit">$1</mark>`);
        }, text);
    }

    function isMatchingSearch(operation) {
        const tokens = getSearchTokens();
        if (tokens.length === 0) {
            return true;
        }

        const amount = getOperationAmount(operation);
        const searchText = normalizeSearchText([
            operation.description,
            operation.category,
            Math.abs(amount),
            amount,
            formatMoney(amount, operation.operationCurrency || "RUB"),
            getOperationType(operation)
        ].join(" "));

        return tokens.every((token) => searchText.includes(token));
    }

    function toDateInputValue(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");
        return `${year}-${month}-${day}`;
    }

    function getOperationTime(operation) {
        return operation.operationDate ? new Date(operation.operationDate).getTime() : 0;
    }

    function getOperationAmount(operation) {
        return Number(operation.operationAmount || 0);
    }

    function compareOperations(first, second) {
        switch (sortMode?.value || "date-desc") {
            case "date-asc":
                return getOperationTime(first) - getOperationTime(second);
            case "amount-desc":
                return Math.abs(getOperationAmount(second)) - Math.abs(getOperationAmount(first));
            case "amount-asc":
                return Math.abs(getOperationAmount(first)) - Math.abs(getOperationAmount(second));
            case "date-desc":
            default:
                return getOperationTime(second) - getOperationTime(first);
        }
    }

    function getSortedOperations() {
        return [...allOperations].sort(compareOperations);
    }

    function isMatchingOperationType(operation) {
        if (!operationTypeFilter) {
            return true;
        }
        const amount = getOperationAmount(operation);
        if (operationTypeFilter.value === "income") {
            return amount >= 0;
        }
        if (operationTypeFilter.value === "expense") {
            return amount < 0;
        }
        return true;
    }

    function isMatchingCategory(operation) {
        if (!categoryFilter) {
            return true;
        }
        return categoryFilter.value === "all" || (operation.category || "Без категории") === categoryFilter.value;
    }

    function isMatchingTransferPerson(operation) {
        if (!categoryFilter || !transferPersonFilter) {
            return true;
        }
        if (categoryFilter.value !== "Переводы" || transferPersonFilter.value === "all") {
            return true;
        }
        return (operation.description || "Без описания") === transferPersonFilter.value;
    }

    function updateCategoryFilterOptions(operations) {
        if (!categoryFilter) {
            return;
        }
        const selectedCategory = categoryFilter.value;
        const categories = Array.from(new Set(
            operations
                .filter(isMatchingOperationType)
                .map((operation) => operation.category || "Без категории")
        )).sort((first, second) => first.localeCompare(second, "ru"));

        categoryFilter.innerHTML = "";
        categoryFilter.append(new Option("Все категории", "all"));
        categories.forEach((category) => {
            categoryFilter.append(new Option(category, category));
        });

        categoryFilter.value = categories.includes(selectedCategory) ? selectedCategory : "all";
    }

    function updateTransferPersonFilterOptions(operations) {
        if (!transferPersonControl || !transferPersonFilter || !categoryFilter) {
            return;
        }
        const isTransferCategory = categoryFilter.value === "Переводы";
        transferPersonControl.hidden = !isTransferCategory;

        if (!isTransferCategory) {
            transferPersonFilter.value = "all";
            return;
        }

        const selectedPerson = transferPersonFilter.value;
        const people = Array.from(new Set(
            operations
                .filter(isMatchingOperationType)
                .filter((operation) => (operation.category || "Без категории") === "Переводы")
                .map((operation) => operation.description || "Без описания")
        )).sort((first, second) => first.localeCompare(second, "ru"));

        transferPersonFilter.innerHTML = "";
        transferPersonFilter.append(new Option("Все", "all"));
        people.forEach((person) => {
            transferPersonFilter.append(new Option(person, person));
        });

        transferPersonFilter.value = people.includes(selectedPerson) ? selectedPerson : "all";
    }

    function getMonthKey(operation) {
        const date = operation.operationDate ? new Date(operation.operationDate) : null;
        return date ? `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}` : "no-date";
    }

    function getOperationYear(operation) {
        const date = operation.operationDate ? new Date(operation.operationDate) : null;
        return date ? date.getFullYear() : null;
    }

    function getMonthLabel(monthKey) {
        if (monthKey === "no-date") {
            return "Без даты";
        }

        const [year, month] = monthKey.split("-").map(Number);
        return new Intl.DateTimeFormat("ru-RU", {
            month: "long",
            year: "numeric"
        }).format(new Date(year, month - 1, 1));
    }

    function getShortMonthLabel(monthIndex) {
        const label = new Intl.DateTimeFormat("ru-RU", {
            month: "short"
        }).format(new Date(2026, monthIndex, 1));
        const cleanLabel = label.replace(".", "");
        return cleanLabel.charAt(0).toUpperCase() + cleanLabel.slice(1);
    }

    function getYears() {
        return Array.from(new Set(allOperations.map(getOperationYear).filter(Boolean)))
            .sort((first, second) => second - first);
    }

    function getMonthKeysForYear(year) {
        const keys = Array.from(new Set(allOperations
            .filter((operation) => getOperationYear(operation) === year)
            .map(getMonthKey)));
        return keys.sort((first, second) => second.localeCompare(first));
    }

    function getLatestMonthPeriod() {
        const latestYear = getYears()[0];
        const latestMonthKey = latestYear ? getMonthKeysForYear(latestYear)[0] : null;
        if (!latestYear || !latestMonthKey) {
            return { type: "all", key: null, from: null, to: null };
        }

        return {
            type: "month",
            key: latestMonthKey,
            year: latestYear,
            from: null,
            to: null
        };
    }

    function isInSelectedPeriod(operation) {
        if (selectedPeriod.type === "all") {
            return true;
        }

        if (selectedPeriod.type === "year") {
            return getOperationYear(operation) === selectedPeriod.year;
        }

        if (selectedPeriod.type === "custom") {
            const time = getOperationTime(operation);
            return time >= selectedPeriod.from.getTime() && time <= selectedPeriod.to.getTime();
        }

        return getMonthKey(operation) === selectedPeriod.key;
    }

    function getCurrentPeriodOperations() {
        return getSortedOperations().filter(isInSelectedPeriod);
    }

    function getChartCategoryGroups() {
        if (currentAnalytics?.categoryGroups) {
            return {
                income: currentAnalytics.categoryGroups.find((group) => group.type === "income")?.items || [],
                expense: currentAnalytics.categoryGroups.find((group) => group.type === "expense")?.items || []
            };
        }
        return { income: [], expense: [] };
    }

    function getChartCategoryValues() {
        const groups = getChartCategoryGroups();
        return [...groups.income, ...groups.expense];
    }

    function getSelectedChartCategoryKeys() {
        return Array.from(customChartCategories.querySelectorAll(".custom-chart-category-input:checked"))
            .map((checkbox) => checkbox.value);
    }

    function getSelectedPeriodRange() {
        if (selectedPeriod.type === "year") {
            return {
                from: `${selectedPeriod.year}-01-01`,
                to: `${selectedPeriod.year}-12-31`
            };
        }
        if (selectedPeriod.type === "month" && selectedPeriod.key) {
            const [year, month] = selectedPeriod.key.split("-").map(Number);
            const lastDay = new Date(year, month, 0).getDate();
            return {
                from: `${selectedPeriod.key}-01`,
                to: `${selectedPeriod.key}-${String(lastDay).padStart(2, "0")}`
            };
        }
        if (selectedPeriod.type === "custom" && selectedPeriod.from && selectedPeriod.to) {
            return {
                from: toDateInputValue(selectedPeriod.from),
                to: toDateInputValue(selectedPeriod.to)
            };
        }
        return {};
    }

    function analyticsQuery() {
        const params = new URLSearchParams();
        const range = getSelectedPeriodRange();
        if (range.from) {
            params.set("from", range.from);
        }
        if (range.to) {
            params.set("to", range.to);
        }
        params.set("metric", customChartMetric?.value || "amount");
        params.set("donutMode", donutMode);

        if (customChartCategories?.children.length) {
            const selectedKeys = getSelectedChartCategoryKeys();
            if (selectedKeys.length === 0) {
                params.append("categoryKeys", "__none__");
            } else {
                selectedKeys.forEach((key) => params.append("categoryKeys", key));
            }
        }
        return params.toString();
    }

    async function loadAnalytics() {
        const token = ++analyticsLoadToken;
        const analytics = await apiFetch(`/finance/analytics?${analyticsQuery()}`);
        if (token !== analyticsLoadToken) {
            return;
        }

        currentAnalytics = analytics;
        updateCustomChartCategoryOptions();
        renderDashboard();
        renderExpenseChart();
        renderCustomChart();
    }

    function setAllChartCategories(checked) {
        customChartCategories.querySelectorAll(".custom-chart-category-input").forEach((checkbox) => {
            checkbox.checked = checked;
        });
        loadAnalytics();
    }

    function setChartCategoriesByType(type, checked) {
        customChartCategories.querySelectorAll(`.custom-chart-category-input[data-type="${type}"]`).forEach((checkbox) => {
            checkbox.checked = checked;
        });
        loadAnalytics();
    }

    function updateCustomChartCategoryOptions() {
        const hadOptions = customChartCategories.children.length > 0;
        const selectedCategoryKeys = new Set(getSelectedChartCategoryKeys());
        const groups = getChartCategoryGroups();

        customChartCategories.innerHTML = "";
        [
            {
                type: "income",
                title: "Доходы",
                icon: "↑",
                items: groups.income,
                styles: {
                    "--group-accent": "#56c8b3",
                    "--group-bg": "#f6fbfa",
                    "--group-border": "#e3efec",
                    "--group-border-strong": "#b7e4db",
                    "--group-divider": "#e7f1ef",
                    "--group-selected-bg": "#eef8f6",
                    "--group-shadow": "rgba(86, 200, 179, 0.1)"
                }
            },
            {
                type: "expense",
                title: "Расходы",
                icon: "↓",
                items: groups.expense,
                styles: {
                    "--group-accent": "#6f99ec",
                    "--group-bg": "#f7faff",
                    "--group-border": "#e2eaf6",
                    "--group-border-strong": "#c3d5f8",
                    "--group-divider": "#e7edf7",
                    "--group-selected-bg": "#eef4ff",
                    "--group-shadow": "rgba(111, 153, 236, 0.1)"
                }
            }
        ].forEach((group) => {
            const section = document.createElement("div");
            section.className = "custom-chart-category-group";
            section.dataset.categoryType = group.type;
            Object.entries(group.styles).forEach(([name, value]) => section.style.setProperty(name, value));

            const header = document.createElement("div");
            header.className = "custom-chart-category-group-header";
            header.innerHTML = `
                <span class="custom-chart-category-group-title">
                    <span class="custom-chart-category-group-icon">${group.icon}</span>
                    <span>${group.title}</span>
                </span>
                <span class="custom-chart-category-group-actions">
                    <button class="custom-chart-category-group-action" type="button" data-category-group="${group.type}" data-category-checked="true">Выбрать</button>
                    <button class="custom-chart-category-group-action" type="button" data-category-group="${group.type}" data-category-checked="false">Убрать</button>
                    <span class="custom-chart-category-counter">${group.items.length}</span>
                </span>
            `;

            const list = document.createElement("div");
            list.className = "custom-chart-category-list";
            group.items.forEach((item) => {
                const label = document.createElement("label");
                label.className = "custom-chart-category";
                const checkbox = document.createElement("input");
                checkbox.className = "custom-chart-category-input";
                checkbox.type = "checkbox";
                checkbox.value = item.key;
                checkbox.dataset.type = item.type;
                checkbox.dataset.category = item.category;
                checkbox.checked = hadOptions ? selectedCategoryKeys.has(item.key) : true;
                const text = document.createElement("span");
                text.className = "custom-chart-category-label";
                text.textContent = item.label;
                label.append(checkbox, text);
                list.append(label);
            });

            section.append(header, list);
            customChartCategories.append(section);
        });
    }

    function getChartSummaryText(total, metric, selectedCategoryKeys, groupingText, chartTypeText) {
        const allCategoryCount = getChartCategoryValues().length;
        const categoryText = selectedCategoryKeys.size === allCategoryCount
            ? "все категории"
            : `категорий: ${selectedCategoryKeys.size}`;
        return `${formatChartValue(total, metric)} за период, ${categoryText}, ${chartTypeText}, ${groupingText}`;
    }

    function getHeatmapColor(value, maxValue, metric) {
        if (value <= 0 || maxValue <= 0) {
            return "#e9edf2";
        }

        const ratio = Math.min(1, value / maxValue);
        if (metric === "count") {
            const lightness = 88 - Math.round(ratio * 48);
            return `hsl(142 55% ${lightness}%)`;
        }

        const lightness = 88 - Math.round(ratio * 46);
        return `hsl(213 68% ${lightness}%)`;
    }

    function formatHeatmapDate(date) {
        return new Intl.DateTimeFormat("ru-RU", {
            day: "numeric",
            month: "long",
            year: "numeric"
        }).format(date);
    }

    function getMondayStart(date) {
        const day = date.getDay() || 7;
        const start = new Date(date.getFullYear(), date.getMonth(), date.getDate());
        start.setDate(start.getDate() - day + 1);
        return start;
    }

    function getWeeksBetween(startDate, date) {
        return Math.floor((getMondayStart(date) - startDate) / 604800000);
    }

    function getYearsBetween(fromDate, toDate) {
        const years = [];
        for (let year = fromDate.getFullYear(); year <= toDate.getFullYear(); year += 1) {
            years.push(year);
        }
        return years;
    }

    function renderBarChart(chartData) {
        customChartCanvas.classList.remove("is-heatmap");
        const { buckets, metric, total, average, max: maxValue, useMonths } = chartData;
        const selectedCategoryKeys = new Set(getSelectedChartCategoryKeys());
        const groupingText = `группировка: ${useMonths ? "по месяцам" : "по дням"}`;
        customChartSummary.textContent = getChartSummaryText(total, metric, selectedCategoryKeys, groupingText, "столбцы");

        if (maxValue === 0) {
            customChartCanvas.innerHTML = `<span class="message">За выбранный период и категории операций нет</span>`;
            return;
        }

        customChartCanvas.innerHTML = `
            <div class="custom-chart-bars" style="--average-height: ${Math.round(average / maxValue * 180)}px">
                <div class="custom-chart-tooltip" data-chart-tooltip></div>
                ${customChartAverage.checked ? `
                    <div class="custom-chart-average-line">
                        <span>Среднее: ${formatChartValue(average, metric)}</span>
                    </div>
                ` : ""}
                ${buckets.map((bucket) => {
                    const height = Math.max(2, Math.round(bucket.value / maxValue * 180));
                    const intensity = maxValue ? bucket.value / maxValue : 0;
                    const topColor = intensity > 0.66 ? "#7CC6FE" : intensity > 0.33 ? "#6EA8FE" : "#B9D7FF";
                    const bottomColor = intensity > 0.66 ? "#5B8DEF" : intensity > 0.33 ? "#5B8DEF" : "#7CC6FE";
                    const tooltip = `${bucket.label}: ${formatChartValue(bucket.value, metric)}`;
                    return `
                        <div class="custom-chart-bar-wrap" tabindex="0" style="--bar-height: ${height}px; --bar-top: ${topColor}; --bar-bottom: ${bottomColor}" aria-label="${tooltip}" data-tooltip="${tooltip}">
                            <div class="custom-chart-bar" style="height: ${height}px"></div>
                            <span class="custom-chart-bar-label">${bucket.label}</span>
                        </div>
                    `;
                }).join("")}
            </div>
        `;
    }

    function renderHeatmapChart(chartData) {
        customChartCanvas.classList.add("is-heatmap");
        const { buckets, metric, total, max: maxValue } = chartData;
        const selectedCategoryKeys = new Set(getSelectedChartCategoryKeys());
        const valueByDate = new Map(buckets.map((bucket) => [bucket.key, bucket.value]));
        const bounds = {
            from: new Date(`${chartData.from}T00:00:00`),
            to: new Date(`${chartData.to}T23:59:59.999`)
        };
        customChartSummary.textContent = getChartSummaryText(total, metric, selectedCategoryKeys, "группировка: по дням", "календарь");

        if (maxValue === 0) {
            customChartCanvas.innerHTML = `<span class="message">За выбранный период и категории операций нет</span>`;
            return;
        }

        const weekdayLabels = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];
        const years = getYearsBetween(bounds.from, bounds.to);
        const yearBlocks = years.map((year) => {
            const yearStart = new Date(year, 0, 1);
            const yearEnd = new Date(year, 11, 31);
            const gridStart = getMondayStart(yearStart);
            const monthLabels = Array.from({ length: 12 }, (_, monthIndex) => {
                const monthDate = new Date(year, monthIndex, 1);
                const column = getWeeksBetween(gridStart, monthDate) + 2;
                return `<span class="heatmap-month" style="grid-column: ${column}; grid-row: 1">${getShortMonthLabel(monthIndex)}</span>`;
            }).join("");
            const weekdayMarkup = weekdayLabels.map((label, index) => (
                `<span class="heatmap-weekday" style="grid-row: ${index + 2}">${label}</span>`
            )).join("");
            const cells = [];
            const cursor = new Date(gridStart);
            const gridEnd = new Date(yearEnd);
            gridEnd.setDate(gridEnd.getDate() + (7 - (gridEnd.getDay() || 7)));

            while (cursor <= gridEnd) {
                const isCurrentYear = cursor.getFullYear() === year;
                const isSelectedPeriod = cursor >= bounds.from && cursor <= bounds.to;
                const key = toDateInputValue(cursor);
                const value = isCurrentYear && isSelectedPeriod ? (valueByDate.get(key) || 0) : 0;
                const week = getWeeksBetween(gridStart, cursor);
                const weekday = cursor.getDay() || 7;
                const tooltip = `${formatHeatmapDate(cursor)}: ${formatChartValue(value, metric)}`;
                const opacity = isCurrentYear && isSelectedPeriod ? 1 : 0.28;
                cells.push(`
                    <span class="heatmap-cell ${value === 0 ? "is-zero" : ""}" tabindex="0" aria-label="${tooltip}" style="grid-column: ${week + 2}; grid-row: ${weekday + 1}; background: ${getHeatmapColor(value, maxValue, metric)}; opacity: ${opacity}">
                        <span class="custom-chart-tooltip">${tooltip}</span>
                    </span>
                `);
                cursor.setDate(cursor.getDate() + 1);
            }

            return `
                <div class="custom-chart-heatmap">
                    <span class="custom-chart-heatmap-year">${year}</span>
                    <div class="heatmap-grid">
                        ${monthLabels}
                        ${weekdayMarkup}
                        ${cells.join("")}
                    </div>
                </div>
            `;
        }).join("");

        customChartCanvas.innerHTML = `<div class="custom-chart-heatmaps">${yearBlocks}</div>`;
    }

    function renderCustomChart() {
        if (!customChartSummary || !customChartCanvas || !customChartType || !customChartAverage) {
            return;
        }
        if (allOperations.length === 0) {
            customChartSummary.textContent = "Нет операций для графика";
            customChartCanvas.innerHTML = `<span class="message">Операции еще не загружены</span>`;
            return;
        }

        const isHeatmap = customChartType.value === "heatmap";
        customChartAverage.disabled = isHeatmap;
        customChartAverage.closest(".custom-chart-toggle").classList.toggle("is-disabled", isHeatmap);

        const chartData = currentAnalytics?.chart;
        if (!chartData || !chartData.from || !chartData.to) {
            customChartSummary.textContent = "Нет операций в выбранном периоде";
            customChartCanvas.innerHTML = `<span class="message">Выбери период, в котором есть операции</span>`;
            return;
        }

        if (getSelectedChartCategoryKeys().length === 0) {
            customChartSummary.textContent = "Выбери хотя бы одну категорию";
            customChartCanvas.innerHTML = `<span class="message">Для построения графика отметь одну или несколько категорий</span>`;
            return;
        }

        if (isHeatmap) {
            renderHeatmapChart(chartData);
            return;
        }

        renderBarChart(chartData);
    }

    function getVisibleOperations() {
        if (!operationsBody) {
            return getCurrentPeriodOperations();
        }
        return getCurrentPeriodOperations()
            .filter(isMatchingOperationType)
            .filter(isMatchingCategory)
            .filter(isMatchingTransferPerson)
            .filter(isMatchingSearch);
    }

    function renderPeriodTabs() {
        if (!periodTabs || !customPeriodForm) {
            selectedPeriod = { type: "all", key: null, from: null, to: null };
            return;
        }
        const years = getYears();
        periodTabs.innerHTML = "";

        if (years.length === 0) {
            selectedPeriod = { type: "all", key: null, from: null, to: null };
            customPeriodForm.classList.remove("visible");
            return;
        }

        if (!periodWasInitialized) {
            selectedPeriod = getLatestMonthPeriod();
            periodWasInitialized = true;
        }

        if (selectedPeriod.type === "year" && !years.includes(selectedPeriod.year)) {
            selectedPeriod = { type: "all", key: null, from: null, to: null };
        }

        if (selectedPeriod.type === "month") {
            const [year] = selectedPeriod.key ? selectedPeriod.key.split("-").map(Number) : [];
            const monthKeys = year ? getMonthKeysForYear(year) : [];
            if (!selectedPeriod.key || !monthKeys.includes(selectedPeriod.key)) {
                selectedPeriod = { type: "all", key: null, from: null, to: null };
            }
        }

        const yearsRow = document.createElement("div");
        yearsRow.className = "period-years";
        periodTabs.appendChild(yearsRow);
        const currentYear = selectedPeriod.year || (selectedPeriod.key ? Number(selectedPeriod.key.split("-")[0]) : null);

        const allButton = document.createElement("button");
        allButton.type = "button";
        allButton.className = `period-tab${selectedPeriod.type === "all" ? " active" : ""}`;
        allButton.dataset.periodType = "all";
        allButton.textContent = "Все";
        yearsRow.appendChild(allButton);

        years.forEach((year) => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = `period-tab${selectedPeriod.type !== "all" && currentYear === year ? " active" : ""}`;
            button.dataset.periodType = "year";
            button.dataset.year = String(year);
            button.textContent = String(year);
            yearsRow.appendChild(button);
        });

        const customButton = document.createElement("button");
        customButton.type = "button";
        customButton.className = `period-tab${selectedPeriod.type === "custom" ? " active" : ""}`;
        customButton.dataset.periodType = "custom";
        customButton.textContent = "Свой период";
        yearsRow.appendChild(customButton);

        if (selectedPeriod.type !== "all" && currentYear) {
            const monthsRow = document.createElement("div");
            monthsRow.className = "period-months";
            periodTabs.appendChild(monthsRow);

            const monthKeys = getMonthKeysForYear(currentYear);

            monthKeys.forEach((monthKey) => {
                const [, month] = monthKey.split("-").map(Number);
                const button = document.createElement("button");
                button.type = "button";
                button.className = `period-tab${selectedPeriod.type === "month" && monthKey === selectedPeriod.key ? " active" : ""}`;
                button.dataset.periodType = "month";
                button.dataset.periodKey = monthKey;
                button.dataset.year = String(currentYear);
                button.textContent = getShortMonthLabel(month - 1);
                monthsRow.appendChild(button);
            });
        }

        customPeriodForm.classList.toggle("visible", selectedPeriod.type === "custom");
    }

    function appendOperationRow(operation) {
        const amount = getOperationAmount(operation);
        const row = document.createElement("tr");
        const amountClass = amount < 0 ? "amount-negative" : "amount-positive";
        const type = getOperationType(operation);
        const category = operation.category || "Без категории";
        const categoryColor = getCategoryColor(category);
        const feedback = operationFeedback.get(Number(operation.id));
        if (feedback) {
            row.classList.add(feedback === "success" ? "edit-success" : "edit-error");
        }
        row.dataset.operationId = operation.id;
        row.innerHTML = `
            <td>
                <input class="operation-checkbox" type="checkbox" value="${operation.id}" aria-label="Выбрать операцию">
            </td>
            <td class="operation-date">${formatOperationDate(operation.operationDate)}</td>
            <td class="operation-type">${highlightSearch(type)}</td>
            <td class="editable-cell" data-edit-field="category" data-id="${operation.id}" tabindex="0" role="button" aria-label="Изменить категорию">
                <span class="editable-value">
                    <span class="category-badge" style="--dot-color: ${categoryColor}; --pill-bg: ${categoryColor}18">${highlightSearch(category)}</span>
                </span>
                <span class="edit-pencil" aria-hidden="true">✎</span>
            </td>
            <td class="editable-cell operation-description" data-edit-field="description" data-id="${operation.id}" tabindex="0" role="button" aria-label="Изменить описание">
                <span class="editable-value">${highlightSearch(operation.description || "Без описания")}</span>
                <span class="edit-pencil" aria-hidden="true">✎</span>
            </td>
            <td class="editable-cell operation-amount ${amountClass}" data-edit-field="operationAmount" data-id="${operation.id}" tabindex="0" role="button" aria-label="Изменить сумму">
                <span class="editable-value">${highlightSearch(formatMoney(amount, operation.operationCurrency || "RUB"))}</span>
                <span class="edit-pencil" aria-hidden="true">✎</span>
            </td>
            <td class="operation-actions">
                <button class="text-danger-button delete-one" type="button" data-id="${operation.id}">Удалить</button>
            </td>
        `;
        operationsBody.appendChild(row);
    }

    function getDonutPoint(percent, radius) {
        const angle = (percent / 100 * 360 - 90) * Math.PI / 180;
        return {
            x: 60 + radius * Math.cos(angle),
            y: 60 + radius * Math.sin(angle)
        };
    }

    function getDonutSegmentPath(startPercent, endPercent) {
        const outerRadius = 48;
        const innerRadius = 28;
        const outerStart = getDonutPoint(startPercent, outerRadius);
        const outerEnd = getDonutPoint(endPercent, outerRadius);
        const innerEnd = getDonutPoint(endPercent, innerRadius);
        const innerStart = getDonutPoint(startPercent, innerRadius);
        const largeArc = endPercent - startPercent > 50 ? 1 : 0;

        return [
            `M ${outerStart.x.toFixed(3)} ${outerStart.y.toFixed(3)}`,
            `A ${outerRadius} ${outerRadius} 0 ${largeArc} 1 ${outerEnd.x.toFixed(3)} ${outerEnd.y.toFixed(3)}`,
            `L ${innerEnd.x.toFixed(3)} ${innerEnd.y.toFixed(3)}`,
            `A ${innerRadius} ${innerRadius} 0 ${largeArc} 0 ${innerStart.x.toFixed(3)} ${innerStart.y.toFixed(3)}`,
            "Z"
        ].join(" ");
    }

    function renderExpenseChart(operations) {
        if (!chartTotal || !categoryPills || !donutWrap || !donut) {
            return;
        }
        const categories = currentAnalytics?.donutCategories || [];
        const total = categories.reduce((sum, category) => sum + category.amount, 0);
        const modeLabel = donutMode === "income" ? "Доходы" : "Расходы";
        const emptyMessage = donutMode === "income" ? "В выбранном периоде доходов нет" : "В выбранном периоде расходов нет";

        chartTotal.textContent = formatWholeMoney(total);
        const chartTitleElement = document.querySelector(".chart-title");
        if (chartTitleElement) {
            chartTitleElement.textContent = modeLabel;
        }
        categoryPills.innerHTML = "";
        donutWrap.querySelectorAll(".donut-percent").forEach((label) => label.remove());

        if (total === 0) {
            donut.style.background = "";
            donut.innerHTML = `
                <svg class="donut-svg" viewBox="0 0 120 120" aria-hidden="true" draggable="false">
                    <path class="donut-track" d="${getDonutSegmentPath(0, 99.999)}"></path>
                </svg>
                <button class="donut-center" type="button" draggable="false" aria-label="Показать ${donutMode === "income" ? "расходы" : "доходы"}">${modeLabel}</button>
            `;
            categoryPills.innerHTML = `<span class="message">${emptyMessage}</span>`;
            return;
        }

        let currentPercent = 0;
        const segments = [];

        categories.forEach((category, index) => {
            const percent = category.amount / total * 100;
            const color = getDonutCategoryColor(category.name, index, donutMode);
            const start = currentPercent;
            const end = Math.min(100, currentPercent + percent);
            const arcEnd = end >= 100 ? 99.999 : end;
            const safeCategoryName = escapeHtml(category.name);
            const safeCategoryMeta = escapeHtml(`${formatWholeMoney(category.amount)} · ${Math.round(percent)}%`);
            segments.push(`
                <path
                    class="donut-segment"
                    d="${getDonutSegmentPath(start, arcEnd)}"
                    style="--segment-color: ${color}"
                    draggable="false"
                    aria-label="${safeCategoryName}: ${safeCategoryMeta}"
                ></path>
            `);
            currentPercent = end;

            const pill = document.createElement("span");
            pill.className = "category-pill";
            pill.style.setProperty("--dot-color", color);
            pill.style.setProperty("--pill-bg", `${color}18`);
            pill.innerHTML = `
                <span class="category-dot">${categoryIcons[category.name] || "•"}</span>
                <span>
                    <span class="category-pill-main">${safeCategoryName}</span>
                    <span class="category-pill-meta">${safeCategoryMeta}</span>
                </span>
            `;
            categoryPills.appendChild(pill);
        });

        donut.style.background = "";
        donut.innerHTML = `
            <svg class="donut-svg" viewBox="0 0 120 120" draggable="false" aria-label="${modeLabel} по категориям">
                <path class="donut-track" d="${getDonutSegmentPath(0, 99.999)}"></path>
                ${segments.join("")}
            </svg>
            <button class="donut-center" type="button" draggable="false" aria-label="Показать ${donutMode === "income" ? "расходы" : "доходы"}">${modeLabel}</button>
        `;
    }

    function renderDashboard() {
        if (!dashboardPeriod || !dashboardTopCategory || !dashboardTopCategoryNote) {
            return;
        }
        const summary = currentAnalytics?.summary;
        const topCategory = summary?.topCategory;

        dashboardPeriod.textContent = getPeriodLabel();
        dashboardTopCategory.textContent = topCategory ? topCategory.name : "—";
        dashboardTopCategoryNote.textContent = topCategory ? formatMoney(topCategory.amount) : "Нет расходов";
        if (income) {
            income.textContent = formatMoney(summary?.incomeTotal || 0);
        }
        if (expenses) {
            expenses.textContent = formatMoney(summary?.expenseTotal || 0);
        }
        if (balance) {
            balance.textContent = formatMoney(summary?.balance || 0);
        }
    }

    async function loadOperations() {
        const [loadedCategories, loadedOperations] = await Promise.all([
            apiFetch("/finance/categories"),
            apiFetch("/finance/operations")
        ]);
        categoryOptions = loadedCategories || [];
        allOperations = loadedOperations || [];
        updateEmptyState();
        if (allOperations.length === 0) {
            return;
        }
        renderPeriodTabs();
        renderOperations();
    }

    function getOperationById(operationId) {
        return allOperations.find((operation) => Number(operation.id) === Number(operationId));
    }

    function getEditableCategories() {
        return Array.from(new Set([
            ...categoryOptions.map((category) => category.name),
            ...allOperations.map((operation) => operation.category || "Без категории"),
        ])).sort((first, second) => first.localeCompare(second, "ru"));
    }

    async function updateOperation(operationId, patch) {
        return apiFetch(`/finance/operations/${operationId}`, {
            method: "PATCH",
            body: patch
        });
    }

    function closeActiveEditor() {
        if (activeEditor && activeEditor.cancel) {
            activeEditor.cancel();
        }
        activeEditor = null;
    }

    function startOperationEdit(cell) {
        const field = cell.dataset.editField;
        const operationId = Number(cell.dataset.id);
        const operation = getOperationById(operationId);
        if (!operation || !field) {
            return;
        }

        closeActiveEditor();

        const previousHtml = cell.innerHTML;
        let editor;

        if (field === "category") {
            editor = document.createElement("select");
            getEditableCategories().forEach((category) => {
                editor.append(new Option(category, category));
            });
            editor.value = operation.category || "Без категории";
        } else {
            editor = document.createElement("input");
            editor.type = field === "operationAmount" ? "number" : "text";
            if (field === "operationAmount") {
                editor.step = "0.01";
                editor.value = String(getOperationAmount(operation));
            } else {
                editor.value = operation.description || "";
            }
        }

        editor.className = "inline-editor";
        cell.classList.add("is-editing");
        cell.innerHTML = "";
        cell.appendChild(editor);
        editor.focus();
        if (editor.select) {
            editor.select();
        }

        let closed = false;
        const cancel = () => {
            if (closed) {
                return;
            }
            closed = true;
            cell.classList.remove("is-editing", "is-saving", "is-error");
            cell.innerHTML = previousHtml;
            activeEditor = null;
        };
        const save = async () => {
            if (closed) {
                return;
            }

            const patch = {};
            if (field === "operationAmount") {
                const nextAmount = Number(editor.value);
                if (!Number.isFinite(nextAmount)) {
                    cell.classList.add("is-error");
                    editor.focus();
                    return;
                }
                patch.operationAmount = nextAmount;
            } else if (field === "category") {
                patch.category = editor.value;
            } else {
                patch.description = editor.value;
            }

            closed = true;
            cell.classList.remove("is-editing", "is-error");
            cell.classList.add("is-saving");
            cell.innerHTML = `<span class="inline-edit-status">Сохранение</span>`;
            activeEditor = null;

            try {
                const updatedOperation = await updateOperation(operationId, patch);
                const index = allOperations.findIndex((item) => Number(item.id) === operationId);
                if (index >= 0) {
                    allOperations[index] = updatedOperation;
                }
                await loadAccount();
                operationFeedback.set(operationId, "success");
                renderOperations();
                setTimeout(() => {
                    operationFeedback.delete(operationId);
                    renderOperations();
                }, 900);
            } catch (error) {
                operationFeedback.set(operationId, "error");
                deleteMessage.textContent = error.message;
                deleteMessage.className = "message error";
                renderOperations();
                setTimeout(() => {
                    operationFeedback.delete(operationId);
                    renderOperations();
                }, 1200);
            }
        };

        editor.addEventListener("keydown", (event) => {
            if (event.key === "Enter") {
                event.preventDefault();
                save();
            }
            if (event.key === "Escape") {
                event.preventDefault();
                cancel();
            }
        });
        editor.addEventListener("blur", save);
        activeEditor = { cancel };
    }

    function renderOperations() {
        const periodOperations = getCurrentPeriodOperations();
        updateCategoryFilterOptions(periodOperations);
        updateTransferPersonFilterOptions(periodOperations);
        const operations = getVisibleOperations();

        if (operationsBody) {
            operationsBody.innerHTML = "";
        }
        if (selectAll) {
            selectAll.checked = false;
        }

        if (operationsBody && operations.length === 0) {
            operationsBody.innerHTML = `<tr><td colspan="7" class="message">${searchQuery.trim() ? "Операции не найдены" : "Операций пока нет"}</td></tr>`;
        }

        if (operationsBody) {
            operations.forEach(appendOperationRow);
        }
        loadAnalytics();
    }

    function getSelectedOperationIds() {
        return Array.from(document.querySelectorAll(".operation-checkbox:checked"))
            .map((checkbox) => Number(checkbox.value));
    }

    async function deleteOperations(ids) {
        return apiFetch("/finance/operations", {
            method: "DELETE",
            body: { ids }
        });
    }

    function toggleFinancePanel(panel) {
        const shouldOpen = !panel.classList.contains("open");
        financePanels.forEach((financePanel) => {
            financePanel.classList.toggle("open", financePanel === panel && shouldOpen);
        });
    }

    function updateSelectedFilesText() {
        const fileCount = financeFile.files.length;
        if (fileCount === 0) {
            fileDropSubtitle.textContent = "или нажми, чтобы выбрать XLS/XLSX";
            return;
        }

        fileDropSubtitle.textContent = fileCount === 1
            ? financeFile.files[0].name
            : `Выбрано файлов: ${fileCount}`;
    }

    function setDroppedFiles(files) {
        const acceptedFiles = Array.from(files).filter((file) => /\.(xlsx|xls)$/i.test(file.name));
        const transfer = new DataTransfer();
        acceptedFiles.forEach((file) => transfer.items.add(file));
        financeFile.files = transfer.files;
        updateSelectedFilesText();
    }

    uploadForm?.addEventListener("submit", async (event) => {
        event.preventDefault();
        uploadMessage.className = "message";
        const fileCount = financeFile.files.length;
        uploadMessage.textContent = fileCount > 1 ? `Загружаю файлов: ${fileCount}...` : "Загружаю файл...";

        const formData = new FormData(uploadForm);

        try {
            const result = await apiFetch("/finance/upload", {
                method: "POST",
                body: formData
            });

            uploadForm.reset();
            updateSelectedFilesText();
            uploadMessage.textContent = `Загружено операций: ${result.importedCount}. Пропущено дублей: ${result.skippedCount}.`;
            uploadMessage.classList.add("success");
            await loadAccount();
            await loadOperations();
        } catch (error) {
            uploadMessage.textContent = error.message;
            uploadMessage.classList.add("error");
        }
    });

    financePanels.forEach((panel) => {
        panel.querySelector(".finance-panel-toggle").addEventListener("click", () => {
            toggleFinancePanel(panel);
        });
    });

    financeFile?.addEventListener("change", updateSelectedFilesText);

    fileDropZone?.addEventListener("dragover", (event) => {
        event.preventDefault();
        fileDropZone.classList.add("drag-over");
    });

    fileDropZone?.addEventListener("dragleave", () => {
        fileDropZone.classList.remove("drag-over");
    });

    fileDropZone?.addEventListener("drop", (event) => {
        event.preventDefault();
        fileDropZone.classList.remove("drag-over");
        setDroppedFiles(event.dataTransfer.files);
    });

    manualForm?.addEventListener("submit", async (event) => {
        event.preventDefault();
        manualMessage.className = "message";
        manualMessage.textContent = "";

        const formData = new FormData(manualForm);
        const operation = {
            type: formData.get("type"),
            amount: Number(formData.get("amount")),
            currency: formData.get("currency"),
            category: formData.get("category"),
            description: formData.get("description")
        };

        try {
            await apiFetch("/finance/operations", {
                method: "POST",
                body: operation
            });

            manualForm.reset();
            document.querySelector("#manual-currency").value = "RUB";
            manualMessage.textContent = "Операция добавлена";
            manualMessage.classList.add("success");
            await loadAccount();
            await loadOperations();
        } catch (error) {
            manualMessage.textContent = error.message;
            manualMessage.classList.add("error");
        }
    });

    manualType?.addEventListener("change", () => {
        manualCategory.placeholder = manualType.value === "income" ? "Прочий доход" : "Прочий расход";
    });

    emptyImportButton?.addEventListener("click", () => {
        openOperationsAction("import");
    });

    emptyManualButton?.addEventListener("click", () => {
        openOperationsAction("manual");
    });

    selectAll?.addEventListener("change", () => {
        document.querySelectorAll(".operation-checkbox").forEach((checkbox) => {
            checkbox.checked = selectAll.checked;
        });
    });

    sortMode?.addEventListener("change", renderOperations);
    operationTypeFilter?.addEventListener("change", renderOperations);
    categoryFilter?.addEventListener("change", renderOperations);
    transferPersonFilter?.addEventListener("change", renderOperations);
    operationSearch?.addEventListener("input", () => {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(() => {
            searchQuery = operationSearch.value;
            renderOperations();
        }, 250);
    });

    function updateChartTypeButtons() {
        if (!customChartType || !chartTypeButtons.length) {
            return;
        }

        const segmentedControl = chartTypeButtons[0]?.closest(".chart-type-segmented");
        segmentedControl?.setAttribute("data-active", customChartType.value);

        chartTypeButtons.forEach((button) => {
            const isActive = button.dataset.chartType === customChartType.value;
            button.classList.toggle("is-active", isActive);
            button.setAttribute("aria-pressed", String(isActive));
        });
    }

    function showChartTooltip(target, event) {
        const tooltip = customChartCanvas?.querySelector("[data-chart-tooltip]");
        if (!tooltip || !target?.dataset.tooltip) {
            return;
        }

        const rect = target.getBoundingClientRect();
        const x = event?.clientX || rect.left + rect.width / 2;
        const y = rect.top;
        tooltip.textContent = target.dataset.tooltip;
        tooltip.style.setProperty("--tooltip-left", `${x}px`);
        tooltip.style.setProperty("--tooltip-top", `${y}px`);
        tooltip.classList.add("is-visible");
    }

    function hideChartTooltip() {
        customChartCanvas?.querySelector("[data-chart-tooltip]")?.classList.remove("is-visible");
    }

    customChartForm?.addEventListener("submit", (event) => {
        event.preventDefault();
        loadAnalytics();
    });

    customChartCategories?.addEventListener("change", loadAnalytics);
    customChartCategories?.addEventListener("click", (event) => {
        const button = event.target.closest(".custom-chart-category-group-action");
        if (!button) {
            return;
        }

        setChartCategoriesByType(button.dataset.categoryGroup, button.dataset.categoryChecked === "true");
    });
    customChartMetric?.addEventListener("change", loadAnalytics);
    customChartType?.addEventListener("change", () => {
        updateChartTypeButtons();
        loadAnalytics();
    });
    chartTypeButtons.forEach((button) => {
        button.addEventListener("click", () => {
            if (!customChartType) {
                return;
            }
            customChartType.value = button.dataset.chartType;
            updateChartTypeButtons();
            loadAnalytics();
        });
    });
    customChartAverage?.addEventListener("change", renderCustomChart);
    customChartClearCategories?.addEventListener("click", () => setAllChartCategories(false));
    customChartSelectCategories?.addEventListener("click", () => setAllChartCategories(true));
    customChartCanvas?.addEventListener("pointermove", (event) => {
        const bar = event.target.closest(".custom-chart-bar-wrap");
        if (bar) {
            showChartTooltip(bar, event);
        }
    });
    customChartCanvas?.addEventListener("pointerleave", hideChartTooltip);
    customChartCanvas?.addEventListener("focusin", (event) => {
        const bar = event.target.closest(".custom-chart-bar-wrap");
        if (bar) {
            showChartTooltip(bar);
        }
    });
    customChartCanvas?.addEventListener("focusout", hideChartTooltip);
    donutWrap?.addEventListener("click", (event) => {
        if (!event.target.closest(".donut-center")) {
            return;
        }

        donutMode = donutMode === "expense" ? "income" : "expense";
        loadAnalytics();
    });

    periodTabs?.addEventListener("click", (event) => {
        const button = event.target.closest(".period-tab");
        if (!button) {
            return;
        }

        if (button.dataset.periodType === "all") {
            selectedPeriod = { type: "all", key: null, from: null, to: null };
        } else if (button.dataset.periodType === "year") {
            selectedPeriod = {
                type: "year",
                year: Number(button.dataset.year),
                key: null,
                from: null,
                to: null
            };
        } else if (button.dataset.periodType === "custom") {
            const monthOperations = getCurrentPeriodOperations();
            const times = monthOperations.map(getOperationTime).filter(Boolean);
            const fallbackFrom = times.length ? new Date(Math.min(...times)) : new Date();
            const fallbackTo = times.length ? new Date(Math.max(...times)) : new Date();
            selectedPeriod = {
                type: "custom",
                key: null,
                from: selectedPeriod.from || new Date(fallbackFrom.getFullYear(), fallbackFrom.getMonth(), fallbackFrom.getDate()),
                to: selectedPeriod.to || new Date(fallbackTo.getFullYear(), fallbackTo.getMonth(), fallbackTo.getDate(), 23, 59, 59, 999)
            };
            document.querySelector("#period-from").value = toDateInputValue(selectedPeriod.from);
            document.querySelector("#period-to").value = toDateInputValue(selectedPeriod.to);
        } else {
            selectedPeriod = {
                type: "month",
                key: button.dataset.periodKey,
                year: Number(button.dataset.year),
                from: null,
                to: null
            };
        }

        renderPeriodTabs();
        renderOperations();
    });

    customPeriodForm?.addEventListener("submit", (event) => {
        event.preventDefault();
        const formData = new FormData(customPeriodForm);
        const fromValue = formData.get("from");
        const toValue = formData.get("to");
        const from = new Date(`${fromValue}T00:00:00`);
        const to = new Date(`${toValue}T23:59:59.999`);

        if (to < from) {
            return;
        }

        selectedPeriod = {
            type: "custom",
            key: null,
            from,
            to
        };
        renderPeriodTabs();
        renderOperations();
    });

    operationsBody?.addEventListener("click", async (event) => {
        const editableCell = event.target.closest(".editable-cell");
        if (editableCell) {
            startOperationEdit(editableCell);
            return;
        }

        const button = event.target.closest(".delete-one");
        if (!button) {
            return;
        }

        deleteMessage.className = "message";
        deleteMessage.textContent = "";

        if (!confirm("Удалить эту операцию?")) {
            return;
        }

        try {
            const result = await deleteOperations([Number(button.dataset.id)]);
            deleteMessage.textContent = `Удалено операций: ${result.deletedCount}`;
            deleteMessage.classList.add("success");
            await loadAccount();
            await loadOperations();
        } catch (error) {
            deleteMessage.textContent = error.message;
            deleteMessage.classList.add("error");
        }
    });

    operationsBody?.addEventListener("keydown", (event) => {
        const editableCell = event.target.closest(".editable-cell");
        if (!editableCell || event.target.classList.contains("inline-editor")) {
            return;
        }
        if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            startOperationEdit(editableCell);
        }
    });

    deleteSelectedButton?.addEventListener("click", async () => {
        deleteMessage.className = "message";
        deleteMessage.textContent = "";

        const ids = getSelectedOperationIds();
        if (ids.length === 0) {
            deleteMessage.textContent = "Выбери хотя бы одну операцию";
            deleteMessage.classList.add("error");
            return;
        }

        if (!confirm(`Удалить выбранные операции: ${ids.length}?`)) {
            return;
        }

        try {
            const result = await deleteOperations(ids);
            deleteMessage.textContent = `Удалено операций: ${result.deletedCount}`;
            deleteMessage.classList.add("success");
            await loadAccount();
            await loadOperations();
        } catch (error) {
            deleteMessage.textContent = error.message;
            deleteMessage.classList.add("error");
        }
    });

    logoutButton.addEventListener("click", async () => {
        await apiFetch("/logout", {
            method: "POST"
        });
        window.location.href = "/login";
    });

    updateChartTypeButtons();
    loadAccount();
    loadOperations();
