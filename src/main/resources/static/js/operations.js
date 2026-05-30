(function () {
    const logoutButton = document.querySelector("#logout");
    const {
        exportOperations,
        getCategories,
        getOperationAmount,
        getOperationType,
        highlightSearch: highlightOperationSearch,
        loadOperationCategories,
        normalizeSearchText
    } = window.OperationUtils;
    const { formatMoney } = window;
    const uploadForm = document.querySelector("#upload-form");
    const uploadMessage = document.querySelector("#upload-message");
    const uploadProgress = document.querySelector("#upload-progress");
    const financeFile = document.querySelector("#finance-file");
    const fileDropZone = document.querySelector("#file-drop-zone");
    const fileDropSubtitle = document.querySelector("#file-drop-subtitle");
    const manualForm = document.querySelector("#manual-form");
    const manualMessage = document.querySelector("#manual-message");
    const manualType = document.querySelector("#manual-type");
    const manualCategory = document.querySelector("#manual-category");
    const operationsBody = document.querySelector("#operations");
    const selectAll = document.querySelector("#select-all");
    const operationSearch = document.querySelector("#operation-search");
    const searchClear = document.querySelector("#search-clear");
    const sortMode = document.querySelector("#sort-mode");
    const operationTypeFilter = document.querySelector("#operation-type-filter");
    const categoryFilter = document.querySelector("#category-filter");
    const transferPersonControl = document.querySelector("#transfer-person-control");
    const transferPersonFilter = document.querySelector("#transfer-person-filter");
    const deleteSelectedButton = document.querySelector("#delete-selected");
    const deleteMessage = document.querySelector("#delete-message");
    const paginationSummary = document.querySelector("#pagination-summary");
    const paginationPage = document.querySelector("#pagination-page");
    const paginationPrev = document.querySelector("#pagination-prev");
    const paginationNext = document.querySelector("#pagination-next");
    const tableFilterSummary = document.querySelector("#table-filter-summary");
    const bulkToolbar = document.querySelector("#bulk-toolbar");
    const bulkCount = document.querySelector("#bulk-count");
    const bulkClear = document.querySelector("#bulk-clear");
    const bulkExport = document.querySelector("#bulk-export");
    const exportCsv = document.querySelector("#export-csv");
    const filtersToggle = document.querySelector("#filters-toggle");
    const filtersPopover = document.querySelector("#filters-popover");
    const filtersBadge = document.querySelector("#filters-badge");
    const filtersReset = document.querySelector("#filters-reset");
    const drawerBackdrop = document.querySelector("#drawer-backdrop");
    const importDrawer = document.querySelector("#import-drawer");
    const manualDrawer = document.querySelector("#manual-drawer");

    let allOperations = [];
    let searchQuery = "";
    let searchTimer = null;
    let activeEditor = null;
    let currentPage = 1;
    let categoryOptions = [];
    const pageSize = 50;
    const operationFeedback = new Map();

    function getSearchTokens() {
        return normalizeSearchText(searchQuery).split(/\s+/).filter(Boolean);
    }

    function highlightSearch(value) {
        return highlightOperationSearch(value, getSearchTokens());
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

    function getOperationTime(operation) {
        return operation.operationDate ? new Date(operation.operationDate).getTime() : 0;
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

    function getVisibleOperations() {
        return getSortedOperations()
            .filter(isMatchingOperationType)
            .filter(isMatchingCategory)
            .filter(isMatchingTransferPerson)
            .filter(isMatchingSearch);
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
        transferPersonControl.classList.toggle("is-hidden", !isTransferCategory);
        transferPersonControl.setAttribute("aria-hidden", String(!isTransferCategory));
        transferPersonFilter.disabled = !isTransferCategory;

        if (!isTransferCategory) {
            transferPersonFilter.value = "all";
            return;
        }

        const selectedPerson = transferPersonFilter.value;
        const people = Array.from(new Set(
            operations
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

    function getActiveFiltersCount() {
        let count = 0;
        if (operationTypeFilter?.value && operationTypeFilter.value !== "all") {
            count++;
        }
        if (categoryFilter?.value && categoryFilter.value !== "all") {
            count++;
        }
        if (categoryFilter?.value === "Переводы" && transferPersonFilter?.value && transferPersonFilter.value !== "all") {
            count++;
        }
        if (sortMode?.value && sortMode.value !== "date-desc") {
            count++;
        }
        return count;
    }

    function updateFilterButtonState() {
        if (!filtersToggle || !filtersBadge) {
            return;
        }
        const activeCount = getActiveFiltersCount();
        filtersBadge.textContent = String(activeCount);
        filtersToggle.classList.toggle("has-active", activeCount > 0);
        filtersToggle.classList.toggle("is-active", activeCount > 0 || filtersPopover?.classList.contains("open"));
    }

    function openFiltersPopover() {
        filtersPopover?.classList.add("open");
        filtersToggle?.setAttribute("aria-expanded", "true");
        updateFilterButtonState();
    }

    function closeFiltersPopover() {
        filtersPopover?.classList.remove("open");
        filtersToggle?.setAttribute("aria-expanded", "false");
        updateFilterButtonState();
    }

    function toggleFiltersPopover() {
        if (filtersPopover?.classList.contains("open")) {
            closeFiltersPopover();
        } else {
            openFiltersPopover();
        }
    }

    function resetFilters() {
        if (operationTypeFilter) {
            operationTypeFilter.value = "all";
        }
        if (categoryFilter) {
            categoryFilter.value = "all";
        }
        if (transferPersonFilter) {
            transferPersonFilter.value = "all";
        }
        if (sortMode) {
            sortMode.value = "date-desc";
        }
        resetPaginationAndRender();
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

    async function loadAccount() {
        await apiFetch("/me");
    }

    async function loadOperations() {
        const [loadedCategories, loadedOperations] = await Promise.all([
            loadOperationCategories(),
            apiFetch("/finance/operations")
        ]);
        categoryOptions = loadedCategories || getCategories();
        allOperations = loadedOperations || [];
        renderOperations();
    }

    async function updateOperation(operationId, patch) {
        return apiFetch(`/finance/operations/${operationId}`, {
            method: "PATCH",
            body: patch
        });
    }

    async function deleteOperations(ids) {
        return apiFetch("/finance/operations", {
            method: "DELETE",
            body: { ids }
        });
    }

    function getSelectedOperationIds() {
        return Array.from(document.querySelectorAll(".operation-checkbox:checked"))
            .map((checkbox) => Number(checkbox.value));
    }

    function updateBulkToolbar() {
        const ids = getSelectedOperationIds();
        bulkToolbar.classList.toggle("visible", ids.length > 0);
        bulkCount.textContent = `Выбрано: ${ids.length}`;
        if (selectAll) {
            const visibleCheckboxes = Array.from(document.querySelectorAll(".operation-checkbox"));
            selectAll.checked = visibleCheckboxes.length > 0 && visibleCheckboxes.every((checkbox) => checkbox.checked);
            selectAll.indeterminate = visibleCheckboxes.some((checkbox) => checkbox.checked) && !selectAll.checked;
        }
    }

    function resetSelection() {
        document.querySelectorAll(".operation-checkbox").forEach((checkbox) => {
            checkbox.checked = false;
        });
        if (selectAll) {
            selectAll.checked = false;
            selectAll.indeterminate = false;
        }
        updateBulkToolbar();
    }

    function updateFilterSummary(visibleCount) {
        const parts = [];
        if (operationTypeFilter?.value === "income") {
            parts.push("только доходы");
        } else if (operationTypeFilter?.value === "expense") {
            parts.push("только расходы");
        }
        if (categoryFilter?.value && categoryFilter.value !== "all") {
            parts.push(categoryFilter.value);
        }
        if (searchQuery.trim()) {
            parts.push(`поиск: “${searchQuery.trim()}”`);
        }
        tableFilterSummary.textContent = parts.length
            ? `${visibleCount} операций, ${parts.join(", ")}`
            : "Все операции";
    }

    function appendOperationRow(operation) {
        operationsBody.appendChild(window.OperationRow.create(operation, {
            feedback: operationFeedback.get(Number(operation.id)),
            searchTokens: getSearchTokens()
        }));
    }

    function renderOperations() {
        updateCategoryFilterOptions(allOperations);
        updateTransferPersonFilterOptions(allOperations);
        updateFilterButtonState();
        const operations = getVisibleOperations();
        const totalPages = Math.max(1, Math.ceil(operations.length / pageSize));
        currentPage = Math.min(currentPage, totalPages);
        const pageOperations = operations.slice((currentPage - 1) * pageSize, currentPage * pageSize);

        operationsBody.innerHTML = "";
        if (pageOperations.length === 0) {
            operationsBody.innerHTML = `<tr><td colspan="7" class="empty-row">${searchQuery.trim() ? "Операции не найдены" : "Операций пока нет"}</td></tr>`;
        } else {
            pageOperations.forEach(appendOperationRow);
        }

        const from = operations.length === 0 ? 0 : (currentPage - 1) * pageSize + 1;
        const to = Math.min(currentPage * pageSize, operations.length);
        paginationSummary.textContent = `${from}-${to} из ${operations.length}`;
        paginationPage.textContent = `${currentPage} / ${totalPages}`;
        paginationPrev.disabled = currentPage <= 1;
        paginationNext.disabled = currentPage >= totalPages;
        updateFilterSummary(operations.length);
        resetSelection();
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
                operationFeedback.set(operationId, "success");
                renderOperations();
                setTimeout(() => {
                    operationFeedback.delete(operationId);
                    renderOperations();
                }, 900);
            } catch (error) {
                operationFeedback.set(operationId, "error");
                deleteMessage.textContent = error.message;
                deleteMessage.className = "status-message error";
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

    function openDrawer(name) {
        const drawer = name === "import" ? importDrawer : manualDrawer;
        drawerBackdrop.classList.add("visible");
        drawer.classList.add("open");
        drawer.setAttribute("aria-hidden", "false");
        setTimeout(() => drawer.querySelector("input, select, button")?.focus(), 80);
    }

    function closeDrawers() {
        [importDrawer, manualDrawer].forEach((drawer) => {
            drawer.classList.remove("open");
            drawer.setAttribute("aria-hidden", "true");
        });
        drawerBackdrop.classList.remove("visible");
    }

    function updateSelectedFilesText() {
        const fileCount = financeFile.files.length;
        if (fileCount === 0) {
            fileDropSubtitle.textContent = "или нажмите, чтобы выбрать XLS/XLSX";
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

    function resetPaginationAndRender() {
        currentPage = 1;
        renderOperations();
    }

    function openPendingDrawerFromDashboard() {
        const drawerName = sessionStorage.getItem("operations:openDrawer");
        if (!drawerName) {
            return;
        }

        sessionStorage.removeItem("operations:openDrawer");
        if (drawerName !== "import" && drawerName !== "manual") {
            return;
        }

        setTimeout(() => openDrawer(drawerName), 120);
    }

    document.querySelectorAll("[data-open-drawer]").forEach((button) => {
        button.addEventListener("click", () => openDrawer(button.dataset.openDrawer));
    });
    document.querySelectorAll("[data-close-drawer]").forEach((button) => {
        button.addEventListener("click", closeDrawers);
    });
    drawerBackdrop.addEventListener("click", closeDrawers);
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            closeActiveEditor();
            closeFiltersPopover();
            closeDrawers();
        }
    });

    filtersToggle?.addEventListener("click", (event) => {
        event.stopPropagation();
        toggleFiltersPopover();
    });

    filtersPopover?.addEventListener("click", (event) => {
        event.stopPropagation();
    });

    document.addEventListener("click", (event) => {
        if (!filtersPopover?.classList.contains("open")) {
            return;
        }
        if (!event.target.closest(".toolbar-filter-wrap")) {
            closeFiltersPopover();
        }
    });

    filtersReset?.addEventListener("click", resetFilters);

    uploadForm?.addEventListener("submit", async (event) => {
        event.preventDefault();
        uploadMessage.className = "status-message";
        const fileCount = financeFile.files.length;
        uploadMessage.textContent = fileCount > 1 ? `Загружаю файлов: ${fileCount}...` : "Загружаю файл...";
        uploadProgress?.classList.add("visible");

        const formData = new FormData(uploadForm);

        try {
            const result = await apiFetch("/finance/upload", {
                method: "POST",
                body: formData
            });

            uploadForm.reset();
            updateSelectedFilesText();
            uploadMessage.textContent = `Загружено: ${result.importedCount}. Дублей: ${result.skippedCount}.`;
            uploadMessage.classList.add("success");
            await loadOperations();
        } catch (error) {
            uploadMessage.textContent = error.message;
            uploadMessage.classList.add("error");
        } finally {
            uploadProgress?.classList.remove("visible");
        }
    });

    financeFile?.addEventListener("change", updateSelectedFilesText);
    fileDropZone?.addEventListener("dragover", (event) => {
        event.preventDefault();
        fileDropZone.classList.add("drag-over");
    });
    fileDropZone?.addEventListener("dragleave", () => fileDropZone.classList.remove("drag-over"));
    fileDropZone?.addEventListener("drop", (event) => {
        event.preventDefault();
        fileDropZone.classList.remove("drag-over");
        setDroppedFiles(event.dataTransfer.files);
    });

    manualForm?.addEventListener("submit", async (event) => {
        event.preventDefault();
        manualMessage.className = "status-message";
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
            await loadOperations();
        } catch (error) {
            manualMessage.textContent = error.message;
            manualMessage.classList.add("error");
        }
    });

    manualType?.addEventListener("change", () => {
        manualCategory.placeholder = manualType.value === "income" ? "Прочий доход" : "Прочий расход";
    });

    selectAll?.addEventListener("change", () => {
        document.querySelectorAll(".operation-checkbox").forEach((checkbox) => {
            checkbox.checked = selectAll.checked;
        });
        updateBulkToolbar();
    });

    operationsBody?.addEventListener("change", (event) => {
        if (event.target.classList.contains("operation-checkbox")) {
            updateBulkToolbar();
        }
    });

    sortMode?.addEventListener("change", resetPaginationAndRender);
    operationTypeFilter?.addEventListener("change", resetPaginationAndRender);
    categoryFilter?.addEventListener("change", () => {
        if (categoryFilter.value !== "Переводы" && transferPersonFilter) {
            transferPersonFilter.value = "all";
        }
        resetPaginationAndRender();
    });
    transferPersonFilter?.addEventListener("change", resetPaginationAndRender);
    operationSearch?.addEventListener("input", () => {
        searchClear.classList.toggle("visible", operationSearch.value.length > 0);
        clearTimeout(searchTimer);
        searchTimer = setTimeout(() => {
            searchQuery = operationSearch.value;
            resetPaginationAndRender();
        }, 180);
    });
    searchClear?.addEventListener("click", () => {
        operationSearch.value = "";
        searchQuery = "";
        searchClear.classList.remove("visible");
        resetPaginationAndRender();
        operationSearch.focus();
    });

    paginationPrev?.addEventListener("click", () => {
        currentPage = Math.max(1, currentPage - 1);
        renderOperations();
    });

    paginationNext?.addEventListener("click", () => {
        currentPage += 1;
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

        deleteMessage.className = "status-message";
        deleteMessage.textContent = "";

        if (!confirm("Удалить эту операцию?")) {
            return;
        }

        try {
            const result = await deleteOperations([Number(button.dataset.id)]);
            deleteMessage.textContent = `Удалено операций: ${result.deletedCount}`;
            deleteMessage.classList.add("success");
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
        deleteMessage.className = "status-message";
        deleteMessage.textContent = "";

        const ids = getSelectedOperationIds();
        if (ids.length === 0) {
            return;
        }

        if (!confirm(`Удалить выбранные операции: ${ids.length}?`)) {
            return;
        }

        try {
            const result = await deleteOperations(ids);
            deleteMessage.textContent = `Удалено операций: ${result.deletedCount}`;
            deleteMessage.classList.add("success");
            await loadOperations();
        } catch (error) {
            deleteMessage.textContent = error.message;
            deleteMessage.classList.add("error");
        }
    });

    bulkClear?.addEventListener("click", resetSelection);
    bulkExport?.addEventListener("click", () => {
        const ids = new Set(getSelectedOperationIds());
        exportOperations(allOperations.filter((operation) => ids.has(Number(operation.id))), "selected-operations.csv");
    });
    exportCsv?.addEventListener("click", () => exportOperations(getVisibleOperations()));

    logoutButton.addEventListener("click", async () => {
        await apiFetch("/logout", {
            method: "POST"
        });
        window.location.href = "/login";
    });

    loadAccount();
    openPendingDrawerFromDashboard();
    loadOperations();
})();
