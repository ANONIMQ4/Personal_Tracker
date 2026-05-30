(function () {
    const {
        formatOperationDate,
        getCategoryColor,
        getOperationAmount,
        highlightSearch
    } = window.OperationUtils;
    const { formatMoney } = window;

    function createCell(className) {
        const cell = document.createElement("td");
        if (className) {
            cell.className = className;
        }
        return cell;
    }

    function setHighlightedContent(element, value, searchTokens) {
        element.innerHTML = highlightSearch(value, searchTokens);
    }

    function createCheckboxCell(operation) {
        const cell = createCell("checkbox-cell");
        const checkbox = document.createElement("input");
        checkbox.className = "operation-checkbox";
        checkbox.type = "checkbox";
        checkbox.value = operation.id;
        checkbox.setAttribute("aria-label", "Выбрать операцию");
        cell.appendChild(checkbox);
        return cell;
    }

    function createTypeCell(amount) {
        const cell = createCell();
        const badge = document.createElement("span");
        const isExpense = amount < 0;
        badge.className = `type-badge ${isExpense ? "expense" : "income"}`;

        const icon = document.createElement("span");
        icon.textContent = isExpense ? "-" : "+";
        badge.append(icon, isExpense ? "Расход" : "Доход");
        cell.appendChild(badge);
        return cell;
    }

    function createEditableCell(operation, field, className, label) {
        const cell = createCell(className);
        cell.classList.add("editable-cell");
        cell.dataset.editField = field;
        cell.dataset.id = operation.id;
        cell.tabIndex = 0;
        cell.role = "button";
        cell.setAttribute("aria-label", label);
        return cell;
    }

    function appendEditableContent(cell, content) {
        const value = document.createElement("span");
        value.className = "editable-value";
        value.appendChild(content);

        const pencil = document.createElement("span");
        pencil.className = "edit-pencil";
        pencil.setAttribute("aria-hidden", "true");
        pencil.textContent = "✎";

        cell.append(value, pencil);
    }

    function createCategoryCell(operation, category, searchTokens) {
        const cell = createEditableCell(operation, "category", "", "Изменить категорию");
        const categoryColor = getCategoryColor(category);
        const badge = document.createElement("span");
        badge.className = "category-badge";
        badge.style.setProperty("--dot-color", categoryColor);
        badge.style.setProperty("--pill-bg", `${categoryColor}18`);

        const text = document.createElement("span");
        setHighlightedContent(text, category, searchTokens);
        badge.appendChild(text);
        appendEditableContent(cell, badge);
        return cell;
    }

    function createDescriptionCell(operation, searchTokens) {
        const cell = createEditableCell(
            operation,
            "description",
            "operation-description",
            "Изменить описание"
        );
        const text = document.createElement("span");
        setHighlightedContent(text, operation.description || "Без описания", searchTokens);
        appendEditableContent(cell, text);
        return cell;
    }

    function createAmountCell(operation, amount, searchTokens) {
        const amountClass = amount < 0 ? "amount-negative" : "amount-positive";
        const cell = createEditableCell(
            operation,
            "operationAmount",
            `operation-amount ${amountClass}`,
            "Изменить сумму"
        );
        const text = document.createElement("span");
        setHighlightedContent(text, formatMoney(amount, operation.operationCurrency || "RUB"), searchTokens);
        appendEditableContent(cell, text);
        return cell;
    }

    function createDeleteCell(operation) {
        const cell = createCell("operation-actions");
        const button = document.createElement("button");
        button.className = "delete-one";
        button.type = "button";
        button.dataset.id = operation.id;
        button.setAttribute("aria-label", "Удалить операцию");
        button.innerHTML = `
            <svg width="15" height="15" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M4 7h16M9 7V5.8A1.8 1.8 0 0 1 10.8 4h2.4A1.8 1.8 0 0 1 15 5.8V7m-8 0 1 13h8l1-13M10 11v5m4-5v5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
        `;
        cell.appendChild(button);
        return cell;
    }

    function createOperationRow(operation, options = {}) {
        const amount = getOperationAmount(operation);
        const category = operation.category || "Без категории";
        const row = document.createElement("tr");
        const feedback = options.feedback;

        if (feedback) {
            row.classList.add(feedback === "success" ? "edit-success" : "edit-error");
        }
        row.dataset.operationId = operation.id;

        row.append(
            createCheckboxCell(operation),
            createCell("operation-date"),
            createTypeCell(amount),
            createCategoryCell(operation, category, options.searchTokens),
            createDescriptionCell(operation, options.searchTokens),
            createAmountCell(operation, amount, options.searchTokens),
            createDeleteCell(operation)
        );
        row.children[1].textContent = formatOperationDate(operation.operationDate);
        return row;
    }

    window.OperationRow = {
        create: createOperationRow
    };
})();
