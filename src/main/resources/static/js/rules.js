const logoutButton = document.querySelector("#logout");
    const ruleForm = document.querySelector("#rule-form");
    const rulePrompt = document.querySelector("#rule-prompt");
    const parseButton = document.querySelector("#parse-button");
    const resetButton = document.querySelector("#reset-button");
    const parseMessage = document.querySelector("#parse-message");
    const ruleResult = document.querySelector("#rule-result");
    const previewResult = document.querySelector("#preview-result");
    const savedRules = document.querySelector("#saved-rules");

    let currentRule = null;
    let currentPrompt = "";
    let previewWasBuilt = false;

    async function api(path, options = {}) {
        return apiFetch(path, options);
    }

    function setMessage(message, type = "") {
        parseMessage.textContent = message;
        parseMessage.className = `status-message ${type}`.trim();
    }

    function confidenceLevel(confidence) {
        if (confidence >= 0.85) {
            return { className: "high", label: "Высокая уверенность" };
        }
        if (confidence >= 0.75) {
            return { className: "medium", label: "Средняя уверенность" };
        }
        return { className: "low", label: "Низкая уверенность" };
    }

    function confidenceBadge(rule) {
        const level = confidenceLevel(rule.confidence);
        return `
            <span class="confidence-badge ${level.className}">
                <span class="confidence-dot"></span>
                ${level.label} · ${Math.round(rule.confidence * 100)}%
            </span>
        `;
    }

    function renderRule(response) {
        currentRule = response.rule;
        previewWasBuilt = false;
        const warnings = response.warnings || [];
        const level = confidenceLevel(currentRule.confidence);
        ruleResult.hidden = false;
        ruleResult.innerHTML = `
            <div class="result-topline">
                <div>
                    <p class="card-kicker">Интерпретация правила</p>
                    <h2>${escapeHtml(currentRule.name)}</h2>
                </div>
                ${confidenceBadge(currentRule)}
            </div>
            ${level.className !== "high" || warnings.length ? renderWarnings(warnings, level) : ""}
            <div class="interpretation">
                <div class="interpretation-block">
                    <p class="interpretation-title">Если</p>
                    <div class="condition-list">${renderConditions(currentRule.conditions)}</div>
                </div>
                <div class="interpretation-block">
                    <p class="interpretation-title">То</p>
                    <div class="action-list">${renderActions(currentRule.actions)}</div>
                </div>
            </div>
            <details class="json-toggle">
                <summary>Показать JSON</summary>
                <pre>${escapeHtml(JSON.stringify(currentRule, null, 2))}</pre>
            </details>
            <div class="actions">
                <button id="preview-button" class="button button-primary" type="button">Показать предпросмотр</button>
                <button id="edit-button" class="button button-secondary" type="button">Изменить текст</button>
            </div>
        `;
        document.querySelector("#preview-button").addEventListener("click", buildPreview);
        document.querySelector("#edit-button").addEventListener("click", () => {
            previewResult.hidden = true;
            rulePrompt.focus();
        });
    }

    function renderWarnings(warnings, level) {
        const messages = warnings.length ? warnings : ["Модель не полностью уверена в интерпретации правила. Проверь условия и действия перед предпросмотром."];
        return `
            <div class="warning-card">
                <strong>${level.label}</strong>
                <div>${messages.map(escapeHtml).join("<br>")}</div>
            </div>
        `;
    }

    function renderConditions(conditions) {
        const rows = [];
        if (conditions.descriptionContains?.length) {
            rows.push(readableRow("Описание содержит", chips(conditions.descriptionContains)));
        }
        if (conditions.categoryIn?.length) {
            rows.push(readableRow("Категория сейчас", chips(conditions.categoryIn)));
        }
        if (conditions.type && conditions.type !== "all") {
            rows.push(readableRow("Тип операции", chips([typeLabel(conditions.type)])));
        }
        if (conditions.amountMin != null || conditions.amountMax != null) {
            const range = `${conditions.amountMin ?? "любая"} — ${conditions.amountMax ?? "любая"}`;
            rows.push(readableRow("Сумма", chips([range])));
        }
        if (conditions.counterpartyContains?.length) {
            rows.push(readableRow("Кому/от кого содержит", chips(conditions.counterpartyContains)));
        }
        return rows.join("") || `<div class="empty-state">Условие не распознано.</div>`;
    }

    function renderActions(actions) {
        const rows = [];
        if (actions.setCategory) {
            rows.push(readableRow("Изменить категорию", chips([actions.setCategory], "action")));
        }
        if (actions.excludeFromAnalytics) {
            rows.push(readableRow("Аналитика", chips(["Исключить из статистики"], "action")));
        }
        if (actions.setCounterparty) {
            rows.push(readableRow("Кому/от кого", chips([actions.setCounterparty], "action")));
        }
        if (actions.markAsTransfer) {
            rows.push(readableRow("Перевод", chips(["Пометить как перевод"], "action")));
        }
        if (actions.renameDescription) {
            rows.push(readableRow("Описание", chips([actions.renameDescription], "action")));
        }
        return rows.join("") || `<div class="empty-state">Действие не распознано.</div>`;
    }

    function readableRow(label, value) {
        return `
            <div class="readable-row">
                <span class="readable-label">${escapeHtml(label)}</span>
                ${value}
            </div>
        `;
    }

    function chips(values, extraClass = "") {
        return `<div class="chip-row">${values.map(value => `<span class="chip ${extraClass}">${escapeHtml(value)}</span>`).join("")}</div>`;
    }

    async function buildPreview() {
        if (!currentRule) {
            return;
        }
        previewResult.hidden = false;
        previewResult.innerHTML = `<div class="empty-state">Строю предпросмотр изменений...</div>`;
        const preview = await api("/api/rules/preview", {
            method: "POST",
            body: JSON.stringify(currentRule)
        });
        previewWasBuilt = true;
        renderPreview(preview);
    }

    function renderPreview(preview) {
        const highImpact = preview.affectedCount > 100;
        previewResult.innerHTML = `
            <div class="result-topline">
                <div>
                    <p class="card-kicker">Предпросмотр изменений</p>
                    <h2>Что изменится</h2>
                </div>
            </div>
            <div class="preview-summary">
                <div>
                    <div class="preview-count">${preview.affectedCount}</div>
                    <div class="muted">операций подходит под правило</div>
                </div>
                <span class="state-badge ${preview.affectedCount ? "ready" : "neutral"}">${preview.affectedCount ? "Готово к проверке" : "Изменений нет"}</span>
            </div>
            ${highImpact ? `<div class="warning-card"><strong>Много изменений</strong><div>Правило затрагивает больше 100 операций. Проверь before → after особенно внимательно.</div></div>` : ""}
            ${preview.affectedCount === 0 ? renderNoPreviewChanges() : renderPreviewTable(preview)}
            <div class="actions">
                <button id="apply-button" class="button button-primary" type="button" ${preview.affectedCount === 0 ? "disabled" : ""}>Применить</button>
                <button id="cancel-button" class="button button-secondary" type="button">Отмена</button>
            </div>
        `;
        document.querySelector("#apply-button")?.addEventListener("click", applyRule);
        document.querySelector("#cancel-button")?.addEventListener("click", resetFlow);
    }

    function renderNoPreviewChanges() {
        return `
            <div class="empty-state">
                Подходящих операций не найдено. Можно изменить формулировку правила или проверить категории и описания операций.
            </div>
        `;
    }

    function renderPreviewTable(preview) {
        return `
            <div class="preview-table">
                <table>
                    <thead>
                    <tr>
                        <th>Операция</th>
                        <th>Изменения</th>
                    </tr>
                    </thead>
                    <tbody>
                    ${preview.changes.map(change => `
                        <tr>
                            <td>
                                <div class="operation-description">${escapeHtml(change.description || "Без описания")}</div>
                                <div class="operation-date">${formatDate(change.date)}</div>
                            </td>
                            <td>${changeHtml(change.before, change.after)}</td>
                        </tr>
                    `).join("")}
                    </tbody>
                </table>
            </div>
        `;
    }

    function changeHtml(before, after) {
        const changes = [];
        if ((before.category || "") !== (after.category || "")) {
            changes.push(transitionLine("Категория", before.category || "—", after.category || "—"));
        }
        if (before.excludeFromAnalytics !== after.excludeFromAnalytics) {
            changes.push(transitionLine("Аналитика", analyticsLabel(before.excludeFromAnalytics), analyticsLabel(after.excludeFromAnalytics)));
        }
        if ((before.counterparty || "") !== (after.counterparty || "")) {
            changes.push(transitionLine("Кому/от кого", before.counterparty || "—", after.counterparty || "—"));
        }
        if ((before.description || "") !== (after.description || "")) {
            changes.push(transitionLine("Описание", before.description || "—", after.description || "—"));
        }
        return `<div class="change-stack">${changes.join("") || `<span class="state-badge neutral">Без изменений</span>`}</div>`;
    }

    function transitionLine(label, before, after) {
        return `
            <div class="change-line">
                <span class="readable-label">${escapeHtml(label)}</span>
                <span class="before-value">${escapeHtml(before)}</span>
                <span class="arrow">→</span>
                <span class="after-value">${escapeHtml(after)}</span>
            </div>
        `;
    }

    async function applyRule() {
        if (!currentRule || !previewWasBuilt) {
            setMessage("Сначала построй предпросмотр", "error");
            return;
        }
        if (!confirm("Применить правило к операциям из предпросмотра?")) {
            return;
        }
        const response = await api("/api/rules/apply", {
            method: "POST",
            body: JSON.stringify({
                rule: currentRule,
                saveRule: true,
                originalPrompt: currentPrompt
            })
        });
        setMessage(`Правило применено. Обновлено операций: ${response.affectedCount}`, "success");
        resetFlow(false);
        await loadRules();
    }

    async function loadRules() {
        const rules = await api("/api/rules");
        savedRules.innerHTML = rules.map((rule, index) => {
            const lastAppliedCount = Number(rule.lastAppliedCount || 0);
            const countLabel = lastAppliedCount > 0
                ? `Затронуто при применении: ${lastAppliedCount}`
                : `Подходит сейчас: ${rule.affectedCount}`;
            return `
            <article class="saved-rule" style="animation-delay: ${Math.min(index * 35, 210)}ms">
                <div class="saved-rule-header">
                    <div>
                        <strong>${escapeHtml(rule.name)}</strong>
                        <span class="saved-rule-prompt">${escapeHtml(rule.originalPrompt || "Без исходного текста")}</span>
                    </div>
                </div>
                <div class="saved-meta">
                    <span class="affected-count">${escapeHtml(countLabel)}</span>
                    <button
                        class="rule-status-toggle ${rule.enabled ? "enabled" : "disabled"}"
                        type="button"
                        data-id="${rule.id}"
                        data-enabled="${rule.enabled}"
                        title="${rule.enabled ? "Нажми, чтобы выключить" : "Нажми, чтобы включить"}"
                    >
                        ${rule.enabled ? "Включено" : "Выключено"}
                    </button>
                    <input class="rule-enabled visually-hidden" type="checkbox" data-id="${rule.id}" ${rule.enabled ? "checked" : ""} tabindex="-1" aria-hidden="true">
                </div>
                <div class="saved-actions">
                    <button class="button button-danger delete-rule" type="button" data-id="${rule.id}">Удалить</button>
                </div>
            </article>
        `;
        }).join("") || `
            <div class="empty-state">
                Пока нет сохранённых правил. Создай первое правило обычным языком.
            </div>
        `;
    }

    function resetFlow(clearText = true) {
        currentRule = null;
        previewWasBuilt = false;
        ruleResult.hidden = true;
        previewResult.hidden = true;
        ruleResult.innerHTML = "";
        previewResult.innerHTML = "";
        if (clearText) {
            rulePrompt.value = "";
            currentPrompt = "";
            setMessage("");
        }
    }

    function typeLabel(type) {
        if (type === "income") {
            return "Доход";
        }
        if (type === "expense") {
            return "Расход";
        }
        return "Доходы и расходы";
    }

    function analyticsLabel(value) {
        return value ? "Исключено из статистики" : "Учитывается в статистике";
    }

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    function formatDate(value) {
        if (!value) {
            return "—";
        }
        return new Intl.DateTimeFormat("ru-RU", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
    }

    document.querySelectorAll(".example-button").forEach((button) => {
        button.addEventListener("click", () => {
            rulePrompt.value = button.textContent.trim();
            rulePrompt.focus();
        });
    });

    ruleForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        currentPrompt = rulePrompt.value.trim();
        if (!currentPrompt) {
            setMessage("Опиши правило обычным языком", "error");
            return;
        }
        setMessage("Разбираю правило...");
        parseButton.disabled = true;
        previewResult.hidden = true;
        try {
            const response = await api("/api/rules/parse", {
                method: "POST",
                body: JSON.stringify({ prompt: currentPrompt })
            });
            renderRule(response);
            setMessage("Правило разобрано. Проверь интерпретацию и построй предпросмотр.", "success");
        } catch (error) {
            setMessage(error.message, "error");
            ruleResult.hidden = true;
        } finally {
            parseButton.disabled = false;
        }
    });

    resetButton.addEventListener("click", () => resetFlow(true));

    savedRules.addEventListener("click", async (event) => {
        const statusToggle = event.target.closest(".rule-status-toggle");
        if (statusToggle) {
            const nextEnabled = statusToggle.dataset.enabled !== "true";
            statusToggle.disabled = true;
            await api(`/api/rules/${statusToggle.dataset.id}/enabled`, {
                method: "PATCH",
                body: JSON.stringify({ enabled: nextEnabled })
            });
            await loadRules();
            return;
        }

        const button = event.target.closest(".delete-rule");
        if (!button) {
            return;
        }
        if (!confirm("Удалить правило?")) {
            return;
        }
        await api(`/api/rules/${button.dataset.id}`, { method: "DELETE" });
        await loadRules();
    });

    logoutButton.addEventListener("click", async () => {
        await apiFetch("/logout", { method: "POST" });
        window.location.href = "/login";
    });

    loadRules();
