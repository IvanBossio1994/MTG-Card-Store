document.addEventListener("DOMContentLoaded", () => {
    const menuButton = document.getElementById("menu-button");
    const appMenu = document.getElementById("app-menu");
    const menuBackdrop = document.getElementById("menu-backdrop");

    if (menuButton && appMenu && menuBackdrop) {
        const closeMenu = () => {
            appMenu.classList.remove("open");
            menuBackdrop.hidden = true;
            menuButton.setAttribute("aria-expanded", "false");
            appMenu.setAttribute("aria-hidden", "true");
            document.body.classList.remove("menu-open");
        };

        menuButton.addEventListener("click", () => {
            const opening = !appMenu.classList.contains("open");
            if (!opening) {
                closeMenu();
                return;
            }

            appMenu.classList.add("open");
            menuBackdrop.hidden = false;
            menuButton.setAttribute("aria-expanded", "true");
            appMenu.setAttribute("aria-hidden", "false");
            document.body.classList.add("menu-open");
        });

        menuBackdrop.addEventListener("click", closeMenu);
        document.addEventListener("keydown", event => {
            if (event.key === "Escape") {
                closeMenu();
            }
        });
    }

    const updateForm = document.getElementById("update-form");
    const importAnalyzeForm = document.getElementById("import-analyze-form");
    const importConfirmForm = document.getElementById("import-confirm-form");
    const storeForm = document.querySelector(".store-form");
    const loadingOverlay = document.getElementById("loading-overlay");

    const confirmWithAppDialog = form => {
        const appConfirmDialog = document.getElementById("app-confirm-dialog");
        if (!appConfirmDialog) {
            return Promise.resolve(window.confirm(form.dataset.confirmMessage || "Confirma la accion."));
        }

        const title = appConfirmDialog.querySelector("#app-confirm-title");
        const message = appConfirmDialog.querySelector("#app-confirm-message");
        const total = appConfirmDialog.querySelector("#app-confirm-total");
        const totalValue = appConfirmDialog.querySelector("[data-app-confirm-total-value]");
        const confirmButton = appConfirmDialog.querySelector("[data-app-confirm-accept]");
        const cancelButtons = appConfirmDialog.querySelectorAll("[data-app-confirm-cancel]");

        if (title) {
            title.textContent = form.dataset.confirmTitle || "Confirmar accion";
        }

        if (message) {
            message.textContent = form.dataset.confirmMessage || "Confirma la accion.";
        }

        if (total && totalValue) {
            const totalText = form.dataset.confirmTotal || "";
            total.hidden = totalText === "" || totalText === "-";
            totalValue.textContent = totalText;
        }

        appConfirmDialog.hidden = false;
        appConfirmDialog.setAttribute("aria-hidden", "false");

        return new Promise(resolve => {
            let resolved = false;

            const finish = value => {
                if (resolved) {
                    return;
                }

                resolved = true;
                appConfirmDialog.hidden = true;
                appConfirmDialog.setAttribute("aria-hidden", "true");
                confirmButton?.removeEventListener("click", confirm);
                cancelButtons.forEach(button => button.removeEventListener("click", cancel));
                appConfirmDialog.removeEventListener("click", backdropCancel);
                document.removeEventListener("keydown", escapeCancel);
                resolve(value);
            };

            const confirm = () => finish(true);
            const cancel = () => finish(false);
            const backdropCancel = event => {
                if (event.target === appConfirmDialog) {
                    finish(false);
                }
            };
            const escapeCancel = event => {
                if (event.key === "Escape") {
                    finish(false);
                }
            };

            confirmButton?.addEventListener("click", confirm);
            cancelButtons.forEach(button => button.addEventListener("click", cancel));
            appConfirmDialog.addEventListener("click", backdropCancel);
            document.addEventListener("keydown", escapeCancel);
            confirmButton?.focus();
        });
    };

    document.querySelectorAll("form[data-app-confirm]").forEach(form => {
        form.dataset.appConfirmBound = "true";
        form.addEventListener("submit", async event => {
            if (form.dataset.appConfirmAccepted === "true") {
                return;
            }

            event.preventDefault();
            const accepted = await confirmWithAppDialog(form);
            if (!accepted) {
                return;
            }

            form.dataset.appConfirmAccepted = "true";
            showLoadingOverlay(
                form,
                form.dataset.loadingButton || "Procesando...",
                form.dataset.loadingTitle || "Procesando accion",
                form.dataset.loadingMessage || "Actualizando Google Sheet y sincronizando cambios..."
            );
            form.submit();
        });
    });

    document.addEventListener("submit", async event => {
        const form = event.target;
        if (!form.matches?.("form[data-app-confirm]") || form.dataset.appConfirmBound === "true") {
            return;
        }

        if (form.dataset.appConfirmAccepted === "true") {
            return;
        }

        event.preventDefault();
        const accepted = await confirmWithAppDialog(form);
        if (!accepted) {
            return;
        }

        form.dataset.appConfirmAccepted = "true";
        showLoadingOverlay(
            form,
            form.dataset.loadingButton || "Procesando...",
            form.dataset.loadingTitle || "Procesando accion",
            form.dataset.loadingMessage || "Actualizando Google Sheet y sincronizando cambios..."
        );
        form.submit();
    });

    const showLoadingOverlay = (form, buttonText, title, message) => {
        if (!loadingOverlay) {
            return;
        }

        loadingOverlay.classList.add("visible");
        loadingOverlay.setAttribute("aria-hidden", "false");

        const loadingTitle = loadingOverlay.querySelector("h2");
        const loadingMessage = loadingOverlay.querySelector("p");

        if (loadingTitle && title) {
            loadingTitle.textContent = title;
        }

        if (loadingMessage && message) {
            loadingMessage.textContent = message;
        }

        const button = form ? form.querySelector("button[type='submit']") : null;
        if (button) {
            button.disabled = true;
            button.textContent = buttonText;
        }
    };

    const hideLoadingOverlay = (form, buttonText) => {
        if (loadingOverlay) {
            loadingOverlay.classList.remove("visible");
            loadingOverlay.setAttribute("aria-hidden", "true");
        }

        const button = form ? form.querySelector("button[type='submit']") : null;
        if (button) {
            button.disabled = false;
            button.textContent = buttonText;
        }
    };

    const showStoreMessage = (success, message) => {
        if (!storeForm) {
            return;
        }

        let alert = storeForm.querySelector(".alert.success, .alert.error");
        if (!alert) {
            alert = document.createElement("p");
            storeForm.prepend(alert);
        }

        alert.id = success ? "" : "store-error";
        alert.className = success ? "alert success" : "alert error";
        alert.hidden = false;
        alert.textContent = message;
    };

    const responseMessage = async (response, fallback) => {
        try {
            const result = await response.json();
            return result.message || fallback;
        } catch (e) {
            return fallback;
        }
    };

    const openDatePicker = (dateInput, event) => {
        if (!dateInput || typeof dateInput.showPicker !== "function") {
            return;
        }

        try {
            event?.preventDefault();
            dateInput.focus();
            dateInput.showPicker();
        } catch (e) {
            // El navegador puede bloquearlo si no lo considera una accion directa del usuario.
        }
    };

    const hiddenInput = (name, value) => {
        const input = document.createElement("input");
        input.type = "hidden";
        input.name = name;
        input.value = value || "";
        return input;
    };

    const refreshPickupAlerts = async () => {
        try {
            const response = await fetch("/api/reservas/retiro/alertas", {
                headers: {"Accept": "application/json"}
            });

            if (!response.ok) {
                return;
            }

            renderPickupAlerts(await response.json());
        } catch (error) {
            console.error(error);
        }
    };

    const renderPickupAlerts = alerts => {
        const searchPanel = document.querySelector(".search-panel");
        if (!searchPanel) {
            return;
        }

        let panel = document.querySelector(".pickup-warning-panel");
        if (!alerts || alerts.length === 0) {
            panel?.remove();
            return;
        }

        if (!panel) {
            panel = document.createElement("section");
            panel.className = "pickup-warning-panel";
            searchPanel.insertAdjacentElement("beforebegin", panel);
        }

        panel.replaceChildren();

        const content = document.createElement("div");
        content.className = "pickup-warning-content";

        const head = document.createElement("div");
        head.className = "pickup-warning-head";
        const titleWrap = document.createElement("div");
        const eyebrow = document.createElement("p");
        eyebrow.className = "eyebrow";
        eyebrow.textContent = "Retiros";
        const title = document.createElement("h2");
        title.textContent = "Pedidos por retirar";
        titleWrap.append(eyebrow, title);
        head.appendChild(titleWrap);

        const createPickupAlertItem = (alert, type) => {
            const item = document.createElement("article");
            item.className = `pickup-warning-item pickup-warning-${type}${alert.overdue ? " overdue" : ""}`;

            const copy = document.createElement("a");
            copy.className = "pickup-warning-copy pickup-warning-link";
            copy.href = `/reservas?openGroup=${encodeURIComponent(alert.groupKey || "")}#${alert.anchorId || ""}`;
            const chip = document.createElement("small");
            chip.className = `pickup-status-chip pickup-status-chip-${type}`;
            chip.textContent = type === "reserved" ? "Reservada" : "Pedido sin stock";
            const client = document.createElement("strong");
            client.textContent = alert.client || "Cliente";
            const date = document.createElement("span");
            date.textContent = alert.overdue
                    ? `Fecha vencida: ${alert.formattedPickupDate || alert.pickupDate || "-"}`
                    : `Retira hoy: ${alert.formattedPickupDate || alert.pickupDate || "-"}`;
            const detail = document.createElement("p");
            detail.textContent = `${alert.cardSummary || "Pedido"} | ${alert.totalQuantity || 0} unidad(es)`
                    + (alert.phone && alert.phone !== "-" ? ` | Tel. ${alert.phone}` : "");
            copy.append(chip, client, date, detail);
            item.appendChild(copy);

            if (alert.overdue) {
                const actions = document.createElement("div");
                actions.className = "pickup-warning-actions";

                if (Number(alert.reservedQuantity) > 0) {
                    const releaseForm = document.createElement("form");
                    releaseForm.method = "post";
                    releaseForm.action = "/reservas/retiro/liberar";
                    releaseForm.dataset.appConfirm = "";
                    releaseForm.dataset.loadingButton = "Liberando...";
                    releaseForm.dataset.loadingTitle = "Cancelando pedido";
                    releaseForm.dataset.loadingMessage = "Cancelando el pedido y actualizando Reservas...";
                    releaseForm.dataset.confirmTitle = "Cancelar pedido vencido";
                    releaseForm.dataset.confirmMessage = `Se cancelara el pedido y ${alert.reservedQuantity} carta(s) volveran a estar disponibles.`;
                    releaseForm.append(
                            hiddenInput("groupKey", alert.groupKey),
                            hiddenInput("currentPickupDate", alert.pickupDate),
                            hiddenInput("returnTo", "/")
                    );
                    const releaseButton = document.createElement("button");
                    releaseButton.className = "secondary-button danger-soft-button compact-action-button";
                    releaseButton.type = "submit";
                    releaseButton.textContent = "Cancelar pedido";
                    releaseForm.appendChild(releaseButton);
                    actions.appendChild(releaseForm);
                }

                const rescheduleForm = document.createElement("form");
                rescheduleForm.className = "pickup-reschedule-form";
                rescheduleForm.method = "post";
                rescheduleForm.action = "/reservas/retiro/reprogramar";
                rescheduleForm.dataset.loadingButton = "Guardando...";
                rescheduleForm.dataset.loadingTitle = "Reprogramando retiro";
                rescheduleForm.dataset.loadingMessage = "Actualizando la fecha de retiro en Reservas...";
                rescheduleForm.append(
                        hiddenInput("groupKey", alert.groupKey),
                        hiddenInput("currentPickupDate", alert.pickupDate),
                        hiddenInput("returnTo", "/")
                );
                const input = document.createElement("input");
                input.className = "pickup-inline-date-input";
                input.name = "pickupDate";
                input.type = "date";
                input.required = true;
                const trigger = document.createElement("button");
                trigger.className = "primary-button compact-action-button pickup-date-trigger";
                trigger.type = "button";
                trigger.textContent = "Cambiar fecha";
                rescheduleForm.append(trigger, input);
                actions.appendChild(rescheduleForm);
                item.appendChild(actions);
            }

            return item;
        };

        const appendPickupAlertGroup = (titleText, groupAlerts, type) => {
            if (!groupAlerts.length) {
                return;
            }

            const section = document.createElement("div");
            section.className = "pickup-warning-section";
            const sectionHead = document.createElement("div");
            sectionHead.className = "pickup-warning-section-head";
            const sectionTitle = document.createElement("h3");
            sectionTitle.textContent = titleText;
            const count = document.createElement("span");
            count.textContent = `(${groupAlerts.length})`;
            sectionTitle.append(" ", count);
            sectionHead.appendChild(sectionTitle);

            const list = document.createElement("div");
            list.className = "pickup-warning-list";
            groupAlerts.forEach(alert => list.appendChild(createPickupAlertItem(alert, type)));
            section.append(sectionHead, list);
            content.appendChild(section);
        };

        const reservedAlerts = alerts.filter(alert => Number(alert.reservedQuantity) > 0);
        const pendingAlerts = alerts.filter(alert => Number(alert.reservedQuantity) <= 0);

        content.appendChild(head);
        appendPickupAlertGroup("Reservadas", reservedAlerts, "reserved");
        appendPickupAlertGroup("Pedidos sin stock", pendingAlerts, "pending");
        panel.append(content);
    };

    if (updateForm && loadingOverlay) {
        updateForm.addEventListener("submit", () => {
            showLoadingOverlay(
                updateForm,
                "Sincronizando...",
                "Sincronizando inventario",
                "Leyendo el Sheet, conectando la app y recalculando precios..."
            );
        });
    }

    if (storeForm && loadingOverlay) {
        storeForm.addEventListener("submit", async event => {
            event.preventDefault();
            showLoadingOverlay(
                storeForm,
                "Guardando...",
                "Guardando configuracion",
                "Preparando cache, revisando credenciales y conectando el Sheet..."
            );

            try {
                const response = await fetch(storeForm.action, {
                    method: "POST",
                    body: new FormData(storeForm),
                    headers: {
                        "X-Requested-With": "fetch",
                        "Accept": "application/json"
                    }
                });

                const result = await response.json();
                hideLoadingOverlay(storeForm, "Guardar configuracion");
                showStoreMessage(Boolean(result.success), result.message || "No se pudo guardar la configuracion.");
            } catch (error) {
                hideLoadingOverlay(storeForm, "Guardar configuracion");
                showStoreMessage(false, "No se pudo guardar la configuracion. Revisa la conexion y volve a intentar.");
            }
        });
    }

    if (importAnalyzeForm && loadingOverlay) {
        const rawList = importAnalyzeForm.querySelector("#rawList");
        const importListError = document.getElementById("import-list-error");

        if (rawList && importListError) {
            rawList.addEventListener("input", () => {
                rawList.classList.remove("invalid-input");
                rawList.removeAttribute("aria-invalid");
                importListError.hidden = true;
            });
        }

        importAnalyzeForm.addEventListener("submit", event => {
            if (rawList && rawList.value.trim() === "") {
                event.preventDefault();
                rawList.classList.add("invalid-input");
                rawList.setAttribute("aria-invalid", "true");

                if (importListError) {
                    importListError.hidden = false;
                    importListError.textContent = "Agrega al menos una carta para analizar la lista.";
                }

                rawList.focus();
                return;
            }

            showLoadingOverlay(
                importAnalyzeForm,
                "Analizando...",
                "Analizando lista",
                "Buscando versiones, revisando stock y comparando resultados..."
            );
        });
    }

    if (importConfirmForm && loadingOverlay) {
        importConfirmForm.addEventListener("submit", async event => {
            if (importConfirmForm.dataset.reservationDecision === "done") {
                showLoadingOverlay(
                    importConfirmForm,
                    "Anadiendo...",
                    "Anadiendo al stock",
                    "Guardando las cartas seleccionadas en Google Sheet..."
                );
                return;
            }

            event.preventDefault();

            const honorReservationsInput = document.getElementById("honor-reservations-input");
            const selectedImportCards = Array.from(importConfirmForm.querySelectorAll(".import-result-checkbox:checked"));
            let pendingCount = 0;

            try {
                const response = await fetch("/api/reservas/pendientes/resumen", {
                    headers: {"Accept": "application/json"}
                });
                const reservationsByKey = response.ok ? await response.json() : {};
                const countedReservationIds = new Set();

                for (const checkbox of selectedImportCards) {
                    const sku = checkbox.dataset.sku || "";
                    const skuParts = sku.split("-");
                    const key = reservationLookupKey(
                        checkbox.dataset.name || "",
                        checkbox.dataset.setName || "",
                        skuParts[0] || "",
                        skuParts.slice(1).join("-"),
                        checkbox.dataset.printing || ""
                    );
                    [
                        ...(reservationsByKey[key] || []),
                        ...(reservationsByKey[flexibleReservationLookupKey(checkbox.dataset.name || "")] || [])
                    ].forEach(reservation => {
                        if (reservation.id && !countedReservationIds.has(reservation.id)) {
                            countedReservationIds.add(reservation.id);
                            pendingCount += 1;
                        }
                    });
                }
            } catch (error) {
                console.error(error);
            }

            if (pendingCount > 0) {
                const separate = await confirmWithAppDialog({
                    dataset: {
                        confirmTitle: "Reservas pendientes",
                        confirmMessage: `Hay ${pendingCount} reserva(s) pendiente(s) entre las cartas seleccionadas. Queres separar esas unidades para reservas antes de sumar stock?`
                    }
                });
                if (honorReservationsInput) {
                    honorReservationsInput.value = separate ? "true" : "false";
                }
            } else if (honorReservationsInput) {
                honorReservationsInput.value = "false";
            }

            importConfirmForm.dataset.reservationDecision = "done";
            showLoadingOverlay(
                importConfirmForm,
                "Anadiendo...",
                "Anadiendo al stock",
                "Guardando las cartas seleccionadas en Google Sheet..."
            );
            importConfirmForm.submit();
        });
    }

    function reservationLookupKey(name, setName, setCode, collectorNumber, printing) {
        return [
            lookupText(name),
            lookupText(setName),
            lookupText(setCode),
            lookupCollectorNumber(collectorNumber),
            lookupPrinting(printing)
        ].join("|");
    }

    function flexibleReservationLookupKey(name) {
        return `${lookupText(name)}|*`;
    }

    function lookupText(value) {
        return String(value || "")
                .trim()
                .toLowerCase()
                .normalize("NFD")
                .replace(/\p{M}/gu, "")
                .replace(/\s+/g, " ");
    }

    function lookupCollectorNumber(value) {
        return lookupText(value).replace(/[^a-z0-9]/g, "").replace(/^0+(?!$)/, "");
    }

    function lookupPrinting(value) {
        const normalized = lookupText(value);
        if (normalized === "foil") {
            return "foil";
        }
        if (normalized === "no foil" || normalized === "non foil" || normalized === "nonfoil") {
            return "nonfoil";
        }
        return normalized;
    }

    document.querySelectorAll("form[data-loading-title]:not([data-app-confirm])").forEach(form => {
        if (form === updateForm || form === storeForm || form === importAnalyzeForm || form === importConfirmForm) {
            return;
        }

        form.addEventListener("submit", () => {
            showLoadingOverlay(
                form,
                form.dataset.loadingButton || "Procesando...",
                form.dataset.loadingTitle,
                form.dataset.loadingMessage || "Actualizando datos..."
            );
        });
    });

    const movementDateInputs = document.querySelectorAll(".movement-date-field input[type='date'], input[type='date'][data-open-datepicker]");

    movementDateInputs.forEach(movementDateInput => {
        const openMovementDatePicker = event => {
            openDatePicker(movementDateInput, event);
        };

        movementDateInput.addEventListener("pointerdown", openMovementDatePicker);
        movementDateInput.addEventListener("mousedown", openMovementDatePicker);
        movementDateInput.addEventListener("keydown", event => {
            if (event.key === "Enter" || event.key === " ") {
                openMovementDatePicker(event);
            }
        });
    });

    document.addEventListener("pointerdown", event => {
        const dateInput = event.target.closest?.("input[type='date'][data-open-datepicker]");
        if (dateInput && !Array.from(movementDateInputs).includes(dateInput)) {
            openDatePicker(dateInput, event);
        }
    });

    document.querySelectorAll("[data-flexible-date-checkbox]").forEach(checkbox => {
        const field = checkbox.closest("label");
        const input = field?.querySelector("[data-flexible-date-input]");
        if (!input) {
            return;
        }

        const syncFlexibleDate = () => {
            input.required = !checkbox.checked;
            input.disabled = checkbox.checked;
            if (checkbox.checked) {
                input.value = "";
            }
        };

        checkbox.addEventListener("change", syncFlexibleDate);
        syncFlexibleDate();
    });

    document.querySelectorAll(".reservation-pickup-edit-form").forEach(form => {
        const input = form.querySelector(".reservation-pickup-edit-input");
        const button = form.querySelector(".reservation-pickup-edit-button");
        if (!input || !button) {
            return;
        }

        button.addEventListener("click", event => {
            openDatePicker(input, event);
        });

        input.addEventListener("change", () => {
            if (input.value) {
                form.requestSubmit();
            }
        });
    });

    document.addEventListener("click", event => {
        const trigger = event.target.closest?.(".pickup-date-trigger");
        if (!trigger) {
            return;
        }

        const input = trigger.closest("form")?.querySelector(".pickup-inline-date-input");
        openDatePicker(input, event);
    });

    document.addEventListener("change", event => {
        const input = event.target.closest?.(".pickup-inline-date-input");
        if (input?.value) {
            input.closest("form")?.requestSubmit();
        }
    });

    const movementFilterForms = document.querySelectorAll(".movement-filter-form");

    movementFilterForms.forEach(form => {
        const dateInput = form.querySelector("input[type='date']");
        const dateError = form.querySelector(".field-error");

        if (!dateInput || !dateError) {
            return;
        }

        dateInput.addEventListener("input", () => {
            dateInput.removeAttribute("aria-invalid");
            dateError.hidden = true;
        });

        form.addEventListener("submit", event => {
            if (dateInput.value.trim() !== "") {
                return;
            }

            event.preventDefault();
            dateInput.setAttribute("aria-invalid", "true");
            dateError.textContent = form.dataset.requiredDateMessage || "Elegi una fecha para filtrar.";
            dateError.hidden = false;
            dateInput.focus();
        });
    });

    const movementTabButtons = document.querySelectorAll(".movement-tab-button");
    const movementTabPanels = document.querySelectorAll(".movement-tab-panel");

    if (movementTabButtons.length > 0 && movementTabPanels.length > 0) {
        movementTabButtons.forEach(button => {
            button.addEventListener("click", () => {
                const targetId = button.dataset.tabTarget;

                movementTabButtons.forEach(tabButton => {
                    const active = tabButton === button;
                    tabButton.classList.toggle("active", active);
                    tabButton.setAttribute("aria-selected", String(active));
                });

                movementTabPanels.forEach(panel => {
                    const active = panel.id === targetId;
                    panel.classList.toggle("active", active);
                    panel.hidden = !active;
                });
            });
        });
    }

    const paginationControls = document.querySelectorAll("[data-table-pagination]");

    paginationControls.forEach(pagination => {
        const table = document.getElementById(pagination.dataset.tablePagination);
        const rows = table ? Array.from(table.querySelectorAll("tbody tr")) : [];
        const pageSize = table ? Number(table.dataset.pageSize || 100) : 100;
        const status = pagination.querySelector(".table-pagination-status");
        const previousButton = pagination.querySelector("[data-page-action='prev']");
        const nextButton = pagination.querySelector("[data-page-action='next']");
        const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
        let currentPage = 1;

        if (!table || rows.length <= pageSize || !status || !previousButton || !nextButton) {
            if (pagination) {
                pagination.hidden = true;
            }
            return;
        }

        const renderPage = () => {
            const startIndex = (currentPage - 1) * pageSize;
            const endIndex = startIndex + pageSize;

            rows.forEach((row, index) => {
                row.hidden = index < startIndex || index >= endIndex;
            });

            status.textContent = `Pagina ${currentPage} de ${totalPages}`;
            previousButton.disabled = currentPage <= 1;
            nextButton.disabled = currentPage >= totalPages;
        };

        previousButton.addEventListener("click", () => {
            if (currentPage <= 1) {
                return;
            }

            currentPage -= 1;
            renderPage();
        });

        nextButton.addEventListener("click", () => {
            if (currentPage >= totalPages) {
                return;
            }

            currentPage += 1;
            renderPage();
        });

        renderPage();
    });

    const searchInput = document.getElementById("q");
    const searchInputs = document.querySelectorAll(".search-control input");
    const searchForm = document.querySelector(".search-form");
    const searchControl = document.querySelector(".search-control");
    const searchFormatError = document.getElementById("search-format-error");

    if (searchInputs.length > 0 && searchControl && searchFormatError) {
        searchInputs.forEach(input => input.addEventListener("input", () => {
            searchControl.classList.remove("invalid");
            searchInput?.removeAttribute("aria-invalid");
            searchFormatError.hidden = true;
        }));
    }

    if (searchForm && searchInputs.length > 0 && searchControl && searchFormatError) {
        searchForm.addEventListener("submit", event => {
            const hasSearchValue = Array.from(searchInputs)
                    .some(input => input.value.trim() !== "");

            if (hasSearchValue) {
                return;
            }

            event.preventDefault();
            searchControl.classList.add("invalid");
            searchInput?.setAttribute("aria-invalid", "true");
            searchFormatError.textContent = "Completa al menos un filtro para buscar.";
            searchFormatError.hidden = false;
            searchInput?.focus();
        });
    }

    const cardSuggestions = document.getElementById("card-suggestions");

    if (searchInput && cardSuggestions) {
        let suggestionTimer = null;
        let suggestionRequest = null;
        let activeSuggestionIndex = -1;
        const suggestionCache = new Map();

        const closeSuggestions = () => {
            cardSuggestions.hidden = true;
            cardSuggestions.innerHTML = "";
            activeSuggestionIndex = -1;
            searchInput.removeAttribute("aria-activedescendant");
        };

        const suggestionButtons = () =>
                Array.from(cardSuggestions.querySelectorAll(".autocomplete-item"));

        const setActiveSuggestion = index => {
            const buttons = suggestionButtons();

            if (buttons.length === 0) {
                return;
            }

            activeSuggestionIndex = (index + buttons.length) % buttons.length;

            buttons.forEach((button, buttonIndex) => {
                const active = buttonIndex === activeSuggestionIndex;
                button.classList.toggle("active", active);
                button.setAttribute("aria-selected", String(active));

                if (active) {
                    searchInput.setAttribute("aria-activedescendant", button.id);
                    button.scrollIntoView({block: "nearest"});
                }
            });
        };

        const selectSuggestion = button => {
            searchInput.value = button.dataset.name || button.textContent.trim();
            closeSuggestions();
            searchInput.focus();
        };

        const renderSuggestions = suggestions => {
            cardSuggestions.innerHTML = "";

            if (!suggestions || suggestions.length === 0) {
                closeSuggestions();
                return;
            }

            suggestions.forEach((suggestion, index) => {
                const button = document.createElement("button");
                button.id = `card-suggestion-${index}`;
                button.className = "autocomplete-item";
                button.type = "button";
                button.role = "option";
                button.dataset.name = suggestion.name || "";
                button.setAttribute("aria-selected", "false");

                const name = document.createElement("strong");
                name.textContent = suggestion.name || "";
                button.appendChild(name);

                if (suggestion.variation) {
                    const detail = document.createElement("small");
                    detail.textContent = suggestion.variation;
                    button.appendChild(detail);
                }

                button.addEventListener("mousedown", event => {
                    event.preventDefault();
                    selectSuggestion(button);
                });

                cardSuggestions.appendChild(button);
            });

            cardSuggestions.hidden = false;
            activeSuggestionIndex = -1;
        };

        const suggestionMatches = (suggestion, value) => {
            const query = value.toLowerCase();
            const name = String(suggestion.name || "").toLowerCase();
            const variation = String(suggestion.variation || "").toLowerCase();
            return name.includes(query) || variation.includes(query);
        };

        const cachedSuggestionsFor = value => {
            const cacheKey = value.toLowerCase();
            if (suggestionCache.has(cacheKey)) {
                return suggestionCache.get(cacheKey);
            }

            let bestPrefix = "";
            let bestSuggestions = null;
            suggestionCache.forEach((suggestions, key) => {
                if (cacheKey.startsWith(key) && key.length > bestPrefix.length) {
                    bestPrefix = key;
                    bestSuggestions = suggestions;
                }
            });

            return bestSuggestions
                    ? bestSuggestions.filter(suggestion => suggestionMatches(suggestion, value))
                    : null;
        };

        const fetchSuggestions = async value => {
            const cacheKey = value.toLowerCase();
            if (suggestionCache.has(cacheKey)) {
                renderSuggestions(suggestionCache.get(cacheKey));
                return;
            }

            if (suggestionRequest) {
                suggestionRequest.abort();
            }

            const request = new AbortController();
            suggestionRequest = request;

            try {
                const response = await fetch(
                        `/api/cartas/sugerencias?q=${encodeURIComponent(value)}`,
                        {signal: request.signal}
                );

                if (!response.ok) {
                    closeSuggestions();
                    return;
                }

                const suggestions = await response.json();
                suggestionCache.set(cacheKey, suggestions);
                renderSuggestions(suggestions);
            } catch (e) {
                if (e.name !== "AbortError") {
                    closeSuggestions();
                }
            } finally {
                if (suggestionRequest === request) {
                    suggestionRequest = null;
                }
            }
        };

        searchInput.addEventListener("input", () => {
            const value = searchInput.value.trim();
            clearTimeout(suggestionTimer);

            if (value.length < 3) {
                closeSuggestions();
                return;
            }

            const cachedSuggestions = cachedSuggestionsFor(value);
            if (cachedSuggestions) {
                renderSuggestions(cachedSuggestions);
            }

            suggestionTimer = setTimeout(() => fetchSuggestions(value), cachedSuggestions ? 0 : 20);
        });

        searchInput.addEventListener("keydown", event => {
            if (cardSuggestions.hidden) {
                return;
            }

            if (event.key === "ArrowDown") {
                event.preventDefault();
                setActiveSuggestion(activeSuggestionIndex + 1);
            } else if (event.key === "ArrowUp") {
                event.preventDefault();
                setActiveSuggestion(activeSuggestionIndex - 1);
            } else if (event.key === "Enter" && activeSuggestionIndex >= 0) {
                event.preventDefault();
                selectSuggestion(suggestionButtons()[activeSuggestionIndex]);
            } else if (event.key === "Escape") {
                closeSuggestions();
            }
        });

        searchInput.addEventListener("blur", () => {
            setTimeout(closeSuggestions, 120);
        });
    }

    const stockSummary = document.getElementById("stock-summary");
    const stockFilterButtons = document.querySelectorAll(".stock-filter-button");
    const stockDeleteDialog = document.getElementById("stock-delete-dialog");
    const pendingReservationModal = document.getElementById("pending-reservation-modal");
    const pendingReservationCardName = document.getElementById("pending-reservation-card-name");
    const pendingReservationList = document.getElementById("pending-reservation-list");
    const pendingReservationConfirm = document.querySelector("[data-pending-reservation-confirm]");
    const pendingReservationStock = document.querySelector("[data-pending-reservation-stock]");
    const reservationModal = document.getElementById("reservation-modal");
    const reservationModalForm = document.getElementById("reservation-modal-form");
    const reservationModalCardName = document.getElementById("reservation-modal-card-name");
    const reservationModalError = document.getElementById("reservation-modal-error");
    const reservationClientOptions = document.getElementById("reservation-client-options");
    const removeFromStockControl = document.getElementById("remove-from-stock-control");
    const addReservationButtons = document.querySelectorAll(".add-reservation-button");
    const conditionPriceSelects = document.querySelectorAll(".condition-price-select");
    const displayedStockValue = valueElement => {
        const rawValue = valueElement?.matches?.("input")
                ? valueElement.value
                : valueElement?.textContent;
        const value = parseInt(rawValue, 10);
        return Number.isNaN(value) ? 0 : Math.max(value, 0);
    };

    const stockChangeQuantity = valueElement => {
        if (!valueElement || !valueElement.matches?.("input")) {
            return 1;
        }

        const displayedValue = displayedStockValue(valueElement);
        if (valueElement.value.trim() === ""
                || valueElement.dataset.stockEdited !== "true") {
            return 1;
        }

        return Math.max(displayedValue, 1);
    };

    const setStockValue = (valueElement, value) => {
        if (!valueElement) {
            return;
        }

        const normalizedValue = String(Math.max(Number(value) || 0, 0));
        if (valueElement.matches?.("input")) {
            valueElement.value = normalizedValue;
            valueElement.dataset.stockValue = normalizedValue;
            valueElement.dataset.stockEdited = "false";
            return;
        }

        valueElement.textContent = normalizedValue;
    };

    const stockChangeMessage = (increase, quantity) => {
        const normalizedQuantity = Math.max(Number(quantity) || 1, 1);
        if (normalizedQuantity === 1) {
            return increase ? "Unidad agregada" : "Unidad vendida";
        }

        return increase
                ? `Unidades ${normalizedQuantity} agregadas`
                : `Unidades ${normalizedQuantity} vendidas`;
    };

    document.addEventListener("input", event => {
        if (event.target?.matches?.("input.stock-value")) {
            event.target.dataset.stockEdited = "true";
        }
    });

    conditionPriceSelects.forEach(select => {
        const row = select.closest(".search-result-row");
        const conditionDataKey = condition => `${condition.toLowerCase()[0].toUpperCase()}${condition.toLowerCase().slice(1)}`;
        const updateConditionPrices = () => {
            if (!row) {
                return;
            }

            const condition = select.value || "NM";
            const dataKey = conditionDataKey(condition);
            const ckPrice = row.dataset[`ck${dataKey}`] || "";
            const localPrice = row.dataset[`local${dataKey}`] || "";
            const rowIndex = row.dataset[`row${dataKey}`] || "0";
            const stockQuantity = Math.max(Number(row.dataset[`stock${dataKey}`]) || 0, 0);
            const reservedQuantity = Math.max(Number(row.dataset[`reserved${dataKey}`]) || 0, 0);
            const availableQuantity = Math.max(Number(row.dataset[`available${dataKey}`] ?? row.dataset[`stock${dataKey}`]) || 0, 0);
            const ckCell = row.querySelector(".ck-price-cell");
            const localCell = row.querySelector(".local-price-cell");
            const stockValue = row.querySelector(".stock-value");
            const increaseButton = row.querySelector(".stock-button.increase");
            const decreaseButton = row.querySelector(".stock-button.decrease");
            const reservationButton = row.querySelector(".add-reservation-button");
            const parentStockControls = row.querySelector(".parent-condition-stock-controls");
            const stockTotalDisplay = row.querySelector(".stock-total-display");
            const selectedConditionStock = {
                condition,
                rowIndex,
                quantity: stockQuantity,
                reservedQuantity,
                availableQuantity,
                action: stockActionForDisplay(stockQuantity, reservedQuantity),
                summary: stockBreakdownText(stockQuantity, reservedQuantity, availableQuantity)
            };
            const hasConditionDropdown = row.nextElementSibling?.classList.contains("inventory-stock-options-row");

            if (ckCell) {
                ckCell.textContent = ckPrice ? `$ ${ckPrice}` : "-";
            }

            if (localCell) {
                localCell.textContent = localPrice ? `$ ${localPrice}` : "-";
            }

            if (stockValue) {
                setStockValue(stockValue, stockQuantity);
            }

            if (increaseButton) {
                increaseButton.dataset.row = rowIndex;
                increaseButton.dataset.condition = condition;
            }

            if (decreaseButton) {
                decreaseButton.dataset.row = rowIndex;
                decreaseButton.dataset.condition = condition;
                decreaseButton.disabled = rowIndex === "0";
            }

            if (reservationButton) {
                reservationButton.dataset.row = rowIndex;
                reservationButton.dataset.condition = condition;
                reservationButton.dataset.stockQuantity = String(availableQuantity);
            }

            if (parentStockControls && stockTotalDisplay) {
                const selectedConditionHasStock = rowIndex !== "0" && availableQuantity > 0;
                parentStockControls.hidden = hasConditionDropdown && selectedConditionHasStock;
                stockTotalDisplay.hidden = !hasConditionDropdown || !selectedConditionHasStock;
                setStockValue(stockTotalDisplay, groupedStockFromConditionDatasets(row).total);
            }

            applyGroupedStockToFamilyRow(row);
            applyStatus(row.querySelector(".stock-action-status"), selectedConditionStock.action);
            setPrimaryReservationMode(row, false, selectedConditionStock);
        };

        if (select.selectedOptions.length === 0 || select.selectedOptions[0].disabled) {
            const firstAvailableOption = Array.from(select.options).find(option => !option.disabled);
            if (firstAvailableOption) {
                select.value = firstAvailableOption.value;
            }
        }

        updateConditionPrices();
        select.addEventListener("change", updateConditionPrices);
    });

    if (reservationModal && reservationModalForm && addReservationButtons.length > 0) {
        let reservationClients = [];
        let reservationClientsLoaded = false;
        let activeReservationButton = null;
        let selectedClientIdentity = null;

        const normalizeClientValue = value => (value || "").trim().toLowerCase();
        const digitsOnly = value => (value || "").replace(/\D/g, "");
        const clientIdentityKey = client => {
            const normalizedClient = normalizeClientValue(client?.client);
            const normalizedDni = digitsOnly(client?.dni || "");
            if (normalizedDni) {
                return `${normalizedClient}|dni:${normalizedDni}`;
            }
            return `${normalizedClient}|phone:${digitsOnly(client?.phone || "")}`;
        };
        const clientOptionLabel = client => {
            if (client.dni) {
                return `${client.client} — DNI ${client.dni}`;
            }
            return client.client;
        };
        const clientOptionValue = client => clientOptionLabel(client);

        const renderReservationClients = () => {
            if (!reservationClientOptions) {
                return;
            }

            reservationClientOptions.replaceChildren();
            reservationClients.forEach(client => {
                if (!client.client) {
                    return;
                }

                const option = document.createElement("option");
                option.value = clientOptionValue(client);
                option.label = clientOptionLabel(client);
                reservationClientOptions.appendChild(option);
            });
        };

        const upsertReservationClientOption = client => {
            if (!client || !client.client) {
                return;
            }

            const nextClient = {
                client: client.client,
                phone: client.phone || "",
                dni: client.dni || ""
            };
            const nextIdentity = clientIdentityKey(nextClient);
            const existingIndex = reservationClients.findIndex(
                    existing => clientIdentityKey(existing) === nextIdentity
            );

            if (existingIndex >= 0) {
                reservationClients[existingIndex] = nextClient;
            } else {
                reservationClients.unshift(nextClient);
            }

            renderReservationClients();
        };

        const loadReservationClients = async () => {
            if (reservationClientsLoaded) {
                return;
            }

            reservationClientsLoaded = true;

            try {
                const response = await fetch("/api/reservas/clientes", {
                    headers: {"Accept": "application/json"}
                });

                if (!response.ok) {
                    return;
                }

                reservationClients = await response.json();
                renderReservationClients();
            } catch (error) {
                console.error(error);
            }
        };

        const selectedReservationClient = () => {
            const clientInput = reservationModalForm.elements.client;
            const value = (clientInput?.value || "").trim();

            if (!value) {
                return null;
            }

            return reservationClients.find(client => clientOptionValue(client) === value) || null;
        };

        const clearSelectedReservationClient = () => {
            if (!selectedClientIdentity) {
                return;
            }

            const selectedClient = reservationClients.find(client => clientIdentityKey(client) === selectedClientIdentity);
            const phoneField = reservationModalForm.elements.phone;
            const dniField = reservationModalForm.elements.dni;
            if (phoneField && digitsOnly(phoneField.value) === digitsOnly(selectedClient?.phone || "")) {
                phoneField.value = "";
            }
            if (dniField && digitsOnly(dniField.value) === digitsOnly(selectedClient?.dni || "")) {
                dniField.value = "";
            }
            selectedClientIdentity = null;
        };

        const applySelectedReservationClient = event => {
            if (event?.type === "input" && selectedClientIdentity) {
                const selectedClient = reservationClients.find(client => clientIdentityKey(client) === selectedClientIdentity);
                if (normalizeClientValue(reservationModalForm.elements.client?.value) !== normalizeClientValue(selectedClient?.client)) {
                    clearSelectedReservationClient();
                }
            }

            const client = selectedReservationClient();
            if (!client) {
                return;
            }

            selectedClientIdentity = clientIdentityKey(client);
            setReservationValue("client", client.client);
            setReservationValue("phone", client.phone);
            setReservationValue("dni", client.dni);
        };

        const restrictNumericField = (field, maxLength) => {
            if (!field) {
                return;
            }

            field.addEventListener("input", () => {
                field.value = digitsOnly(field.value).slice(0, maxLength);
            });
        };

        const closeReservationModal = () => {
            reservationModal.hidden = true;
            reservationModal.setAttribute("aria-hidden", "true");
            reservationModalForm.reset();
            selectedClientIdentity = null;
            reservationModalForm.querySelector("[data-flexible-date-checkbox]")?.dispatchEvent(new Event("change"));

            if (reservationModalError) {
                reservationModalError.hidden = true;
                reservationModalError.textContent = "";
            }
        };

        const setReservationValue = (name, value) => {
            const field = reservationModalForm.elements[name];
            if (field) {
                field.value = value || "";
            }
        };

        const syncRemoveFromStockControl = () => {
            if (!removeFromStockControl || !activeReservationButton) {
                return;
            }

            const checkbox = removeFromStockControl.querySelector("input[type='checkbox']");
            const stockQuantity = Number(activeReservationButton.dataset.stockQuantity || 0);
            const canReserveFromStock = stockQuantity > 0
                    && activeReservationButton.dataset.row
                    && activeReservationButton.dataset.row !== "0";
            removeFromStockControl.hidden = !canReserveFromStock;
            if (checkbox) {
                checkbox.disabled = !canReserveFromStock;
                checkbox.checked = canReserveFromStock;
            }
        };

        restrictNumericField(reservationModalForm.elements.phone, 15);
        restrictNumericField(reservationModalForm.elements.dni, 15);

        document.addEventListener("click", async event => {
            const button = event.target.closest(".add-reservation-button");
            if (!button) {
                return;
            }

            event.preventDefault();
                activeReservationButton = button;
                await loadReservationClients();
                setReservationValue("status", "Sin Stock");
                setReservationValue("name", button.dataset.name);
                setReservationValue("setName", button.dataset.setName);
                setReservationValue("setCode", button.dataset.setCode);
                setReservationValue("collectorNumber", button.dataset.collectorNumber);
                setReservationValue("printing", button.dataset.printing);
                setReservationValue("condition", button.dataset.condition);
                setReservationValue("rowIndex", button.dataset.row);
                setReservationValue("quantity", "1");
                setReservationValue("pickupDate", "");
                const pickupFlexibleCheckbox = reservationModalForm.querySelector("[data-flexible-date-checkbox]");
                if (pickupFlexibleCheckbox) {
                    pickupFlexibleCheckbox.checked = false;
                    pickupFlexibleCheckbox.dispatchEvent(new Event("change"));
                }

                syncRemoveFromStockControl();

                if (reservationModalCardName) {
                    const parts = [
                        button.dataset.name,
                        button.dataset.setName,
                        button.dataset.setCode && button.dataset.collectorNumber
                                ? `${button.dataset.setCode}-${button.dataset.collectorNumber}`
                                : "",
                        button.dataset.printing
                    ].filter(Boolean);
                    reservationModalCardName.textContent = parts.join(" | ");
                }

                reservationModal.hidden = false;
                reservationModal.setAttribute("aria-hidden", "false");
                reservationModalForm.elements.client?.focus();
        });

        reservationModalForm.elements.client?.addEventListener("input", applySelectedReservationClient);
        reservationModalForm.elements.client?.addEventListener("change", applySelectedReservationClient);
        reservationModalForm.elements.flexibleMatch?.addEventListener("change", syncRemoveFromStockControl);

        reservationModal.querySelectorAll(".modal-close, .modal-cancel").forEach(button => {
            button.addEventListener("click", closeReservationModal);
        });

        reservationModal.addEventListener("click", event => {
            if (event.target === reservationModal) {
                closeReservationModal();
            }
        });

        document.addEventListener("keydown", event => {
            if (event.key === "Escape" && !reservationModal.hidden) {
                closeReservationModal();
            }
        });

        reservationModalForm.addEventListener("submit", async event => {
            event.preventDefault();

            if (reservationModalError) {
                reservationModalError.hidden = true;
                reservationModalError.textContent = "";
            }

            const submitButton = reservationModalForm.querySelector("button[type='submit']");
            if (submitButton) {
                submitButton.disabled = true;
                submitButton.textContent = "Guardando...";
            }
            showLoadingOverlay(
                reservationModalForm,
                "Guardando...",
                "Guardando pedido",
                "Registrando el cliente y actualizando Reservas..."
            );

            try {
                const removeFromStockCheckbox = removeFromStockControl?.querySelector("input[type='checkbox']");
                if (removeFromStockCheckbox && removeFromStockControl.hidden) {
                    removeFromStockCheckbox.checked = false;
                    removeFromStockCheckbox.disabled = true;
                }
                const reserveSelectedStock = Boolean(removeFromStockCheckbox?.checked && !removeFromStockCheckbox.disabled);

                const response = await fetch(reservationModalForm.action, {
                    method: "POST",
                    body: new FormData(reservationModalForm),
                    headers: {
                        "X-Requested-With": "fetch",
                        "Accept": "application/json"
                    }
                });

                if (!response.ok) {
                    const message = await responseMessage(response, "No se pudo guardar el pedido.");
                    if (reservationModalError) {
                        reservationModalError.textContent = message;
                        reservationModalError.hidden = false;
                    }
                    return;
                }

                const result = await response.json();
                if (Number(result.stockQuantity) >= 0 && result.rowIndex) {
                    syncStockRows(
                            String(result.rowIndex),
                            Number(result.stockQuantity),
                            result.action || "",
                            Number(result.availableQuantity),
                            Number(result.reservedQuantity),
                            result.snapshot,
                            activeReservationButton
                    );
                } else if (activeReservationButton) {
                    applyStockSnapshot(result.snapshot, activeReservationButton);
                }
                if (!reserveSelectedStock && activeReservationButton) {
                    updateProductPendingIndicator(activeReservationButton, result.pendingInfo || result.snapshot?.pendingInfo);
                    updateFamilyPendingIndicator(activeReservationButton, result.familyPendingInfo || result.snapshot?.pendingInfo);
                }

                upsertReservationClientOption({
                    client: result.client || reservationModalForm.elements.client?.value,
                    phone: result.phone || reservationModalForm.elements.phone?.value,
                    dni: result.dni || reservationModalForm.elements.dni?.value
                });

                showToast(result.message || "Pedido guardado en Reservas", "success");
                await refreshPickupAlerts();
                closeReservationModal();
            } catch (error) {
                console.error(error);
                if (reservationModalError) {
                    reservationModalError.textContent = "No se pudo guardar el pedido. Revisa la conexion y volve a intentar.";
                    reservationModalError.hidden = false;
                }
            } finally {
                hideLoadingOverlay(reservationModalForm, "Guardar pedido");
                if (submitButton) {
                    submitButton.disabled = false;
                    submitButton.textContent = "Guardar pedido";
                }
            }
        });
    }

    document.querySelectorAll("[data-reservation-client-form]").forEach(form => {
        const clientInput = form.elements.client;
        const phoneInput = form.elements.phone;
        const dniInput = form.elements.dni;
        const clientOptions = form.querySelector("[data-reservation-client-options]");
        let reservationClients = [];
        let loaded = false;
        let selectedClientIdentity = null;
        const normalizeClientValue = value => (value || "").trim().toLowerCase();
        const digitsOnly = value => (value || "").replace(/\D/g, "");
        const clientIdentityKey = client => {
            const normalizedClient = normalizeClientValue(client?.client);
            const normalizedDni = digitsOnly(client?.dni || "");
            if (normalizedDni) {
                return `${normalizedClient}|dni:${normalizedDni}`;
            }
            return `${normalizedClient}|phone:${digitsOnly(client?.phone || "")}`;
        };
        const clientOptionLabel = client => {
            if (client.dni) {
                return `${client.client} — DNI ${client.dni}`;
            }
            return client.client;
        };
        const clientOptionValue = client => clientOptionLabel(client);

        const restrictNumericField = (field, maxLength) => {
            if (!field) {
                return;
            }

            field.addEventListener("input", () => {
                field.value = digitsOnly(field.value).slice(0, maxLength);
            });
        };

        const renderReservationClients = () => {
            if (!clientOptions) {
                return;
            }

            clientOptions.replaceChildren();
            reservationClients.forEach(client => {
                if (!client.client) {
                    return;
                }

                const option = document.createElement("option");
                option.value = clientOptionValue(client);
                option.label = clientOptionLabel(client);
                clientOptions.appendChild(option);
            });
        };

        const loadReservationClients = async () => {
            if (loaded) {
                return;
            }

            loaded = true;

            try {
                const response = await fetch("/api/reservas/clientes", {
                    headers: {"Accept": "application/json"}
                });

                if (!response.ok) {
                    return;
                }

                reservationClients = await response.json();
                renderReservationClients();
            } catch (error) {
                console.error(error);
            }
        };

        const selectedReservationClient = () => {
            const value = (clientInput?.value || "").trim();
            if (!value) {
                return null;
            }

            return reservationClients.find(item => clientOptionValue(item) === value) || null;
        };

        const clearSelectedReservationClient = () => {
            if (!selectedClientIdentity) {
                return;
            }

            const selectedClient = reservationClients.find(item => clientIdentityKey(item) === selectedClientIdentity);
            if (phoneInput && digitsOnly(phoneInput.value) === digitsOnly(selectedClient?.phone || "")) {
                phoneInput.value = "";
            }
            if (dniInput && digitsOnly(dniInput.value) === digitsOnly(selectedClient?.dni || "")) {
                dniInput.value = "";
            }
            selectedClientIdentity = null;
        };

        const applySelectedReservationClient = event => {
            if (event?.type === "input" && selectedClientIdentity) {
                const selectedClient = reservationClients.find(item => clientIdentityKey(item) === selectedClientIdentity);
                if (normalizeClientValue(clientInput?.value) !== normalizeClientValue(selectedClient?.client)) {
                    clearSelectedReservationClient();
                }
            }

            const client = selectedReservationClient();
            if (!client) {
                return;
            }

            selectedClientIdentity = clientIdentityKey(client);
            if (clientInput) {
                clientInput.value = client.client;
            }

            if (phoneInput) {
                phoneInput.value = client.phone || "";
            }
            if (dniInput) {
                dniInput.value = client.dni || "";
            }
        };

        restrictNumericField(phoneInput, 15);
        restrictNumericField(dniInput, 15);
        clientInput?.addEventListener("focus", loadReservationClients);
        clientInput?.addEventListener("input", applySelectedReservationClient);
        clientInput?.addEventListener("change", applySelectedReservationClient);
    });

    stockFilterButtons.forEach(stockFilterButton => {
        const table = stockFilterButton.closest("table");
        const stockFilterRows = table
                ? table.querySelectorAll(".search-product-aggregate-row, .search-result-row, .stock-filter-row")
                : [];

        if (stockFilterRows.length <= 0) {
            return;
        }

        const applyStockFilter = filterActive => {
            stockFilterButton.setAttribute("aria-pressed", String(filterActive));
            stockFilterButton.classList.toggle("active", filterActive);
            stockFilterButton.title = filterActive
                ? "Mostrar todas las cartas"
                : "Mostrar solo cartas con stock local";

            stockFilterRows.forEach(row => {
                row.hidden = filterActive && row.dataset.inStock !== "true";
            });

        };

        stockFilterButton.addEventListener("click", () => {
            const filterActive = stockFilterButton.getAttribute("aria-pressed") !== "true";
            applyStockFilter(filterActive);
        });
    });

    const logoInput = document.getElementById("storeLogo");
    const logoFileName = document.getElementById("logo-file-name");
    const logoPreview = document.getElementById("store-logo-preview");
    const removeLogoButton = document.getElementById("remove-logo-button");

    const defaultLogo = "/images/tcg-default-logo.png";

    if (logoInput && logoFileName && logoPreview) {
        logoInput.addEventListener("change", () => {
            const removeLogoInput = document.getElementById("removeLogo");

            if (removeLogoInput) {
                removeLogoInput.value = "false";
            }

            const file = logoInput.files[0];

            logoFileName.textContent = file
                ? file.name
                : "PNG, JPG o WEBP, hasta 2 MB";

            if (!file) {
                logoPreview.src = defaultLogo;
                return;
            }
            const reader = new FileReader();

            reader.onload = (e) => {
                logoPreview.src = e.target.result;
            };
            reader.readAsDataURL(file);
        });
    }

    if (removeLogoButton && logoInput && logoPreview) {
        const removeLogoInput = document.getElementById("removeLogo");
        removeLogoButton.addEventListener("click", () => {
            logoInput.value = "";
            logoPreview.src = defaultLogo;
            logoFileName.textContent = "PNG, JPG o WEBP, hasta 2 MB";

            if (removeLogoInput) {
                removeLogoInput.value = "true";
            }
        });
    }

    const spreadsheetInput = document.getElementById("spreadsheetId");
    const inventorySheetInput = document.getElementById("inventorySheetName");
    const inventorySheetError = document.getElementById("inventory-sheet-error");
    const storeError = document.getElementById("store-error");

    if (spreadsheetInput && storeError) {
        spreadsheetInput.addEventListener("input", () => {
            storeError.hidden = true;
        });
    }

    if (inventorySheetInput && inventorySheetError) {
        inventorySheetInput.addEventListener("input", () => {
            inventorySheetInput.classList.remove("invalid-input");
            inventorySheetError.hidden = true;
        });
    }

    const selectAllImportResults = document.getElementById("select-all-import-results");
    const selectAllImportResultsLabel = document.getElementById("select-all-import-results-label");
    const importResultCheckboxes = document.querySelectorAll(".import-result-checkbox");
    let updateImportSelectionState = () => {};

    if (selectAllImportResults && importResultCheckboxes.length > 0) {
        const allImportCheckboxes = () => Array.from(importResultCheckboxes);
        const visibleImportCheckboxes = () => allImportCheckboxes()
                .filter(checkbox => checkbox.offsetParent !== null);

        updateImportSelectionState = () => {
            const checkboxes = allImportCheckboxes();
            const visibleCheckboxes = visibleImportCheckboxes();
            const selectedCount = checkboxes.filter(item => item.checked).length;

            selectAllImportResults.checked = visibleCheckboxes.length > 0
                    && visibleCheckboxes.every(item => item.checked);
            selectAllImportResults.indeterminate = visibleCheckboxes.some(item => item.checked)
                    && !selectAllImportResults.checked;

            if (selectAllImportResultsLabel) {
                selectAllImportResultsLabel.textContent =
                        `Seleccionar todas las cartas (${selectedCount} seleccionadas)`;
            }
        };

        selectAllImportResults.addEventListener("change", () => {
            visibleImportCheckboxes().forEach(checkbox => {
                checkbox.checked = selectAllImportResults.checked;
            });

            updateImportSelectionState();
        });

        importResultCheckboxes.forEach(checkbox => {
            checkbox.addEventListener("change", updateImportSelectionState);
        });

        updateImportSelectionState();
    }

    document.querySelectorAll(".inventory-stock-toggle").forEach(button => {
        const optionsRow = document.getElementById(button.getAttribute("aria-controls"));
        if (optionsRow) {
            optionsRow.hidden = true;
        }
        button.setAttribute("aria-expanded", "false");
    });

    document.addEventListener("click", event => {
        const button = event.target.closest(".version-toggle");
        if (!button) {
            return;
        }

        const optionsRow = document.getElementById(button.getAttribute("aria-controls"));

        if (!optionsRow) {
            return;
        }

        const shouldOpen = optionsRow.hidden;
        optionsRow.hidden = !shouldOpen;
        button.setAttribute("aria-expanded", String(shouldOpen));
        updateImportSelectionState();
    });

    document.addEventListener("keydown", event => {
        if (event.key !== "Escape") {
            return;
        }

        document.querySelectorAll(".version-toggle[aria-expanded='true']").forEach(button => {
            const optionsRow = document.getElementById(button.getAttribute("aria-controls"));

            if (optionsRow) {
                optionsRow.hidden = true;
            }

            button.setAttribute("aria-expanded", "false");
        });

        updateImportSelectionState();
    });

    const tutorialOverlay = document.getElementById("tutorial-overlay");

    if (tutorialOverlay) {

        const steps = document.querySelectorAll(".tutorial-step");
        const nextButton = document.getElementById("tutorial-next");
        const prevButton = document.getElementById("tutorial-prev");
        const finishTutorialButton = document.getElementById("finish-tutorial");
        const closeTutorialButtons = document.querySelectorAll(".tutorial-close");

        let currentStep = 0;

        const renderProgress = (index) => {
            document.querySelectorAll(".tutorial-progress").forEach(progress => {
                progress.replaceChildren();

                steps.forEach((step, stepIndex) => {
                    const dot = document.createElement("span");
                    dot.classList.toggle("active", stepIndex === index);
                    progress.appendChild(dot);
                });
            });
        };

        const closeTutorial = async () => {
            try {
                await fetch("/tutorial/completar", {
                    method: "POST"
                });
            } catch (e) {
                console.error(e);
            }

            tutorialOverlay.remove();
        };

        const showStep = (index) => {

            steps.forEach(step => {
                step.classList.remove("active");
            });

            steps[index].classList.add("active");
            renderProgress(index);

            if (prevButton) {
                prevButton.disabled = index === 0;
            }

            if (nextButton) {
                nextButton.disabled = index === steps.length - 1;
            }
        };

        if (nextButton) {
            nextButton.addEventListener("click", () => {
                if (currentStep >= steps.length - 1) {
                    return;
                }

                currentStep++;

                showStep(currentStep);
            });
        }

        if (prevButton) {
            prevButton.addEventListener("click", () => {
                if (currentStep <= 0) {
                    return;
                }

                currentStep--;

                showStep(currentStep);
            });
        }

        showStep(currentStep);

        if (finishTutorialButton) {

            finishTutorialButton.addEventListener("click", async () => {
                await closeTutorial();
            });
        }

        closeTutorialButtons.forEach(button => {
            button.addEventListener("click", closeTutorial);
        });

        tutorialOverlay.addEventListener("click", event => {
            if (event.target === tutorialOverlay) {
                closeTutorial();
            }
        });

        document.addEventListener("keydown", event => {
            if (event.key === "Escape" && document.body.contains(tutorialOverlay)) {
                closeTutorial();
            }
        });
    }

    let pendingReservationSelection = null;

    function primaryInventoryRowFromButton(button) {
        const row = button?.closest("tr");
        if (!row) {
            return null;
        }

        if (row.classList.contains("inventory-stock-options-row")) {
            return row.previousElementSibling;
        }

        return row;
    }

    function applyStockBreakdown(row, summary) {
        if (!row) {
            return;
        }

        const targetCell = row.querySelector(".card-name-cell") || row.querySelector("td");
        if (!targetCell) {
            return;
        }

        let breakdown = row.querySelector(".stock-inline-breakdown");
        if (!summary) {
            breakdown?.remove();
            return;
        }

        if (!breakdown) {
            breakdown = document.createElement("small");
            breakdown.className = "stock-inline-breakdown family-stock-breakdown";
            targetCell.appendChild(breakdown);
        }

        breakdown.textContent = summary;
    }

    function stockBreakdownText(quantity, reservedQuantity, availableQuantity) {
        const total = Math.max(Number(quantity) || 0, 0);
        const reserved = Math.max(Number(reservedQuantity) || 0, 0);
        const available = Math.max(Number(availableQuantity) || 0, 0);

        return `${total} total | ${reserved} reservadas | ${available} disponibles`;
    }

    function stockActionForDisplay(quantity, reservedQuantity, fallbackAction = "") {
        const total = Math.max(Number(quantity) || 0, 0);
        const reserved = Math.max(Number(reservedQuantity) || 0, 0);
        if (reserved > 0) {
            return "Reservada";
        }
        if (total <= 0) {
            return "Sin Stock";
        }
        return fallbackAction || "En Stock";
    }

    function conditionDataKey(condition) {
        const normalized = (condition || "").toLowerCase();
        return normalized ? `${normalized[0].toUpperCase()}${normalized.slice(1)}` : "";
    }

    function groupedStockFromConditionDatasets(row) {
        const conditionKeys = ["Nm", "Ex", "Vg", "G"];
        let total = 0;
        let reserved = 0;
        let hasConditionData = false;

        conditionKeys.forEach(key => {
            const stockValue = row?.dataset?.[`stock${key}`];
            const reservedValue = row?.dataset?.[`reserved${key}`];
            if (stockValue != null || reservedValue != null) {
                hasConditionData = true;
            }
            total += Math.max(Number(stockValue) || 0, 0);
            reserved += Math.max(Number(reservedValue) || 0, 0);
        });

        if (!hasConditionData) {
            total = Math.max(Number(row?.dataset?.stockTotal) || 0, 0);
            reserved = Math.max(Number(row?.dataset?.reservedTotal) || 0, 0);
        }

        return {
            total,
            reserved,
            available: Math.max(total - reserved, 0)
        };
    }

    function applyGroupedStockToFamilyRow(row) {
        if (!row) {
            return null;
        }

        const grouped = groupedStockFromConditionDatasets(row);
        row.dataset.stockTotal = String(grouped.total);
        row.dataset.reservedTotal = String(grouped.reserved);
        row.dataset.availableTotal = String(grouped.available);
        row.dataset.inStock = String(grouped.available > 0);
        row.classList.toggle("in-stock", grouped.available > 0);
        applyStockBreakdown(row, stockBreakdownText(grouped.total, grouped.reserved, grouped.available));
        return grouped;
    }

    function selectedConditionStockState(button) {
        const condition = button?.dataset?.condition || "NM";
        const option = button?.closest?.(".inventory-condition-option");
        if (option) {
            return {
                reserved: Math.max(Number(option.dataset.reservedQuantity) || 0, 0),
                available: Math.max(Number(option.dataset.availableQuantity ?? option.dataset.stockQuantity) || 0, 0)
            };
        }

        const row = primaryInventoryRowFromButton(button) || button?.closest?.(".search-result-row");
        const key = conditionDataKey(condition);
        if (!row || !key) {
            return {
                reserved: 0,
                available: 0
            };
        }

        return {
            reserved: Math.max(Number(row.dataset[`reserved${key}`] ?? row.dataset.reservedTotal) || 0, 0),
            available: Math.max(Number(row.dataset[`available${key}`] ?? row.dataset.availableTotal) || 0, 0)
        };
    }

    function normalizedStockSnapshot(snapshot) {
        const total = Math.max(Number(snapshot?.stockTotal) || 0, 0);
        const reserved = Math.max(Number(snapshot?.reservedQuantity) || 0, 0);
        const available = Math.max(Number(snapshot?.availableQuantity) || 0, 0);
        return {
            rowIndex: String(snapshot?.rowIndex || "0"),
            stockTotal: total,
            reservedQuantity: reserved,
            availableQuantity: available,
            action: snapshot?.action || stockActionForDisplay(total, reserved),
            summary: snapshot?.summary || stockBreakdownText(total, reserved, available),
            conditionStocks: Array.isArray(snapshot?.conditionStocks)
                    ? snapshot.conditionStocks
                            .filter(stock => String(stock.rowIndex || "0") !== "0")
                            .map(stock => ({
                                condition: stock.condition || "",
                                quantity: Math.max(Number(stock.quantity) || 0, 0),
                                availableQuantity: Math.max(Number(stock.availableQuantity) || 0, 0),
                                reservedQuantity: Math.max(Number(stock.reservedQuantity) || 0, 0),
                                action: stock.action || stockActionForDisplay(stock.quantity, stock.reservedQuantity),
                                rowIndex: String(stock.rowIndex || "0"),
                                ckPriceUsd: stock.ckPriceUsd || "",
                                localPrice: stock.localPrice || ""
                            }))
                    : []
        };
    }

    function applyStatus(status, action) {
        if (!status) {
            return;
        }

        const normalizedAction = action || "Sin Stock";
        status.textContent = normalizedAction;
        status.classList.remove("con-stock", "en-stock", "sin-stock", "reservada");
        status.classList.add(normalizedAction.toLowerCase().replace(/\s+/g, "-"));
    }

    function stockFromConditionOption(option) {
        if (!option) {
            return null;
        }

        return {
            condition: option.dataset.condition || "",
            quantity: Math.max(Number(option.dataset.stockQuantity) || 0, 0),
            reservedQuantity: Math.max(Number(option.dataset.reservedQuantity) || 0, 0),
            availableQuantity: Math.max(Number(option.dataset.availableQuantity ?? option.dataset.stockQuantity) || 0, 0),
            action: option.querySelector(".stock-action-status")?.textContent?.trim() || "",
            rowIndex: String(option.dataset.row || "0"),
            ckPriceUsd: option.querySelectorAll(".condition-meta-cell strong")[0]?.textContent?.replace(/^\$\s*/, "") || "",
            localPrice: option.querySelectorAll(".condition-meta-cell strong")[1]?.textContent?.replace(/^\$\s*/, "") || ""
        };
    }

    function baseCardDataset(primaryRow) {
        const source = primaryRow?.querySelector(".add-reservation-button")
                || primaryRow?.querySelector(".stock-button.increase");
        const sourceData = source?.dataset || {};
        const rowData = primaryRow?.dataset || {};

        return {
            sku: sourceData.sku || rowData.sku || "",
            name: sourceData.name || rowData.name || "",
            setName: sourceData.setName || rowData.setName || "",
            setCode: sourceData.setCode || rowData.setCode || "",
            collectorNumber: sourceData.collectorNumber || rowData.collectorNumber || "",
            printing: sourceData.printing || rowData.printing || "",
            condition: sourceData.condition || rowData.condition || ""
        };
    }

    function conditionPriceFromRow(primaryRow, stock, priceType) {
        const key = conditionDataKey(stock.condition);
        const datasetValue = key ? primaryRow.dataset[`${priceType}${key}`] : "";
        const stockValue = priceType === "ck" ? stock.ckPriceUsd : stock.localPrice;
        const existingOption = primaryRow.nextElementSibling?.classList?.contains("inventory-stock-options-row")
                ? Array.from(primaryRow.nextElementSibling.querySelectorAll(".inventory-condition-option"))
                        .find(option => String(option.dataset.row || "0") === String(stock.rowIndex || "0")
                                || (stock.condition && option.dataset.condition === stock.condition))
                : null;
        const optionStock = existingOption ? stockFromConditionOption(existingOption) : null;
        const optionValue = priceType === "ck" ? optionStock?.ckPriceUsd : optionStock?.localPrice;
        const primaryPriceCells = primaryRow.querySelectorAll(".price-cell");
        const primaryValue = (priceType === "ck" ? primaryPriceCells[0] : primaryPriceCells[1])
                ?.textContent
                ?.replace(/^\$\s*/, "")
                ?.trim();
        return stockValue || optionValue || datasetValue || (primaryValue === "-" ? "" : primaryValue) || "";
    }

    function setPrimaryReservationMode(primaryRow, expanded, stock = null) {
        const reservationCell = primaryRow?.children[primaryRow.children.length - 1];
        if (!reservationCell || !reservationCell.querySelector(".add-reservation-button, .muted-cell")) {
            return;
        }

        if (expanded) {
            const muted = document.createElement("span");
            muted.className = "muted-cell";
            muted.textContent = "Elegir condicion";
            reservationCell.replaceChildren(muted);
            return;
        }

        if (!stock) {
            return;
        }

        const cardData = baseCardDataset(primaryRow);
        const button = document.createElement("button");
        button.type = "button";
        button.className = "secondary-button add-reservation-button";
        button.textContent = "Anadir a pedido";
        button.dataset.name = cardData.name || "";
        button.dataset.setName = cardData.setName || "";
        button.dataset.setCode = cardData.setCode || "";
        button.dataset.collectorNumber = cardData.collectorNumber || "";
        button.dataset.printing = cardData.printing || "";
        button.dataset.condition = stock.condition || cardData.condition || "NM";
        button.dataset.row = stock.rowIndex || "0";
        button.dataset.stockQuantity = String(stock.availableQuantity || 0);
        reservationCell.replaceChildren(button);
    }

    function ensureStockTotalDisplay(primaryRow, total) {
        const stockCell = primaryRow?.children[0];
        if (!stockCell) {
            return null;
        }

        let display = stockCell.querySelector(".stock-total-display");
        if (!display) {
            display = document.createElement("span");
            display.className = "stock-total-display";
            stockCell.appendChild(display);
        }

        setStockValue(display, total);
        display.hidden = false;
        return display;
    }

    function ensureConditionToggle(primaryRow, optionsRow) {
        const nameContainer = primaryRow?.querySelector(".inventory-card-name");
        if (!nameContainer || !optionsRow) {
            return null;
        }

        let toggle = nameContainer.querySelector(".inventory-stock-toggle");
        if (!toggle) {
            toggle = document.createElement("button");
            toggle.className = "version-toggle inventory-stock-toggle";
            toggle.type = "button";
            toggle.setAttribute("aria-label", "Ver stock por condicion");
            nameContainer.appendChild(toggle);
        }

        if (!optionsRow.id) {
            optionsRow.id = `ajax-stock-options-${Date.now()}-${Math.random().toString(36).slice(2)}`;
        }

        toggle.setAttribute("aria-controls", optionsRow.id);
        toggle.setAttribute("aria-expanded", String(!optionsRow.hidden));
        return toggle;
    }

    function ensureConditionOptionsRow(primaryRow) {
        let optionsRow = primaryRow?.nextElementSibling?.classList?.contains("inventory-stock-options-row")
                ? primaryRow.nextElementSibling
                : null;
        if (optionsRow) {
            return optionsRow;
        }

        optionsRow = document.createElement("tr");
        optionsRow.className = "version-options-row inventory-stock-options-row";
        optionsRow.hidden = true;

        const cell = document.createElement("td");
        cell.colSpan = primaryRow?.children?.length || 1;

        const header = document.createElement("div");
        header.className = "version-options-header";
        const label = document.createElement("span");
        label.textContent = "Stock en Sheet por condicion";
        const count = document.createElement("strong");
        header.append(label, count);

        const grid = document.createElement("div");
        grid.className = "version-option-grid inventory-condition-grid";
        cell.append(header, grid);
        optionsRow.appendChild(cell);
        primaryRow?.after(optionsRow);
        ensureConditionToggle(primaryRow, optionsRow);
        return optionsRow;
    }

    function renderConditionOption(primaryRow, stock) {
        const cardData = baseCardDataset(primaryRow);
        const option = document.createElement("div");
        option.className = "version-option inventory-condition-option";
        option.dataset.row = stock.rowIndex || "0";
        option.dataset.condition = stock.condition || "";
        option.dataset.stockQuantity = String(stock.quantity || 0);
        option.dataset.reservedQuantity = String(stock.reservedQuantity || 0);
        option.dataset.availableQuantity = String(stock.availableQuantity || 0);

        const controls = document.createElement("div");
        controls.className = "stock-controls condition-stock-controls";

        const decrease = document.createElement("button");
        decrease.type = "button";
        decrease.className = "stock-button decrease";
        decrease.textContent = "-";
        decrease.dataset.row = stock.rowIndex || "0";
        decrease.dataset.condition = stock.condition || "";
        decrease.disabled = !stock.rowIndex || stock.rowIndex === "0";
        decrease.title = decrease.disabled
                ? "Esta condicion todavia no existe en tu Sheet"
                : (stock.quantity <= 0 ? "Eliminar carta del Sheet" : "Registrar unidad vendida");

        const input = document.createElement("input");
        input.className = "stock-value";
        input.type = "number";
        input.min = "0";
        input.inputMode = "numeric";
        input.value = String(stock.quantity || 0);
        input.dataset.stockValue = String(stock.quantity || 0);
        input.dataset.stockEdited = "false";
        input.setAttribute("aria-label", "Cantidad para modificar stock");

        const increase = document.createElement("button");
        increase.type = "button";
        increase.className = "stock-button increase";
        increase.textContent = "+";
        increase.dataset.row = stock.rowIndex || "0";
        increase.dataset.condition = stock.condition || "";
        increase.dataset.sku = cardData.sku || "";
        increase.dataset.name = cardData.name || "";
        increase.dataset.setName = cardData.setName || "";
        increase.dataset.setCode = cardData.setCode || "";
        increase.dataset.collectorNumber = cardData.collectorNumber || "";
        increase.dataset.printing = cardData.printing || "";
        increase.disabled = !stock.rowIndex || stock.rowIndex === "0";
        increase.title = increase.disabled ? "Esta condicion todavia no existe en tu Sheet" : "Agregar una unidad";
        controls.append(decrease, input, increase);

        const conditionCell = document.createElement("span");
        conditionCell.className = "condition-cell";
        const conditionLabel = document.createElement("strong");
        conditionLabel.textContent = stock.condition || "-";
        const summary = document.createElement("small");
        summary.className = "condition-stock-summary";
        summary.textContent = stockBreakdownText(stock.quantity, stock.reservedQuantity, stock.availableQuantity);
        conditionCell.append(conditionLabel, summary);

        const ckCell = document.createElement("span");
        ckCell.className = "condition-meta-cell";
        const ckLabel = document.createElement("small");
        ckLabel.textContent = "CK USD";
        const ckValue = document.createElement("strong");
        const ckPrice = conditionPriceFromRow(primaryRow, stock, "ck");
        ckValue.textContent = ckPrice ? `$ ${ckPrice}` : "-";
        ckCell.append(ckLabel, ckValue);

        const localCell = document.createElement("span");
        localCell.className = "condition-meta-cell";
        const localLabel = document.createElement("small");
        localLabel.textContent = "Precio local";
        const localValue = document.createElement("strong");
        const localPrice = conditionPriceFromRow(primaryRow, stock, "local");
        localValue.textContent = localPrice ? `$ ${localPrice}` : "-";
        localCell.append(localLabel, localValue);

        const status = document.createElement("span");
        status.className = "status stock-action-status";
        applyStatus(status, stock.action);

        option.append(controls, conditionCell, ckCell, localCell, status);

        if (primaryRow?.querySelector(".add-reservation-button, .muted-cell")) {
            const reservation = document.createElement("button");
            reservation.type = "button";
            reservation.className = "secondary-button add-reservation-button condition-reservation-button";
            reservation.textContent = "Anadir a pedido";
            reservation.dataset.name = cardData.name || "";
            reservation.dataset.setName = cardData.setName || "";
            reservation.dataset.setCode = cardData.setCode || "";
            reservation.dataset.collectorNumber = cardData.collectorNumber || "";
            reservation.dataset.printing = cardData.printing || "";
            reservation.dataset.row = stock.rowIndex || "0";
            reservation.dataset.condition = stock.condition || "";
            reservation.dataset.stockQuantity = String(stock.availableQuantity || 0);
            option.appendChild(reservation);
        }

        return option;
    }

    function applyPrimarySnapshot(primaryRow, state) {
        primaryRow.dataset.stockTotal = String(state.stockTotal);
        primaryRow.dataset.reservedTotal = String(state.reservedQuantity);
        primaryRow.dataset.availableTotal = String(state.availableQuantity);
        primaryRow.dataset.inStock = String(state.availableQuantity > 0);
        primaryRow.classList.toggle("in-stock", state.availableQuantity > 0);
        applyStatus(primaryRow.querySelector(".stock-action-status"), state.action);
        applyStockBreakdown(primaryRow, state.summary);
        updateProductAggregate(primaryRow.dataset.productKey);
    }

    function applyConditionDataset(primaryRow, stock) {
        const key = conditionDataKey(stock.condition);
        if (!key) {
            return;
        }

        primaryRow.dataset[`stock${key}`] = String(stock.quantity || 0);
        primaryRow.dataset[`reserved${key}`] = String(stock.reservedQuantity || 0);
        primaryRow.dataset[`available${key}`] = String(stock.availableQuantity || 0);
        primaryRow.dataset[`row${key}`] = stock.rowIndex || "0";
    }

    function renderExpandedConditionStock(primaryRow, state) {
        const optionsRow = ensureConditionOptionsRow(primaryRow);
        const grid = optionsRow.querySelector(".inventory-condition-grid");
        const stockCell = primaryRow.children[0];
        const parentControls = stockCell?.querySelector(".parent-condition-stock-controls, .stock-controls:not(.condition-stock-controls)");
        if (parentControls) {
            parentControls.hidden = true;
        }
        ensureStockTotalDisplay(primaryRow, state.stockTotal);

        state.conditionStocks.forEach(stock => applyConditionDataset(primaryRow, stock));
        applyPrimarySnapshot(primaryRow, state);

        const conditionCell = primaryRow.children[2];
        if (conditionCell && !conditionCell.querySelector(".condition-price-select")) {
            conditionCell.replaceChildren();
            const value = document.createElement("span");
            value.className = "condition-inline-value";
            value.textContent = "-";
            conditionCell.appendChild(value);
        }

        setPrimaryReservationMode(primaryRow, true);
        if (grid) {
            grid.replaceChildren(...state.conditionStocks.map(stock => renderConditionOption(primaryRow, stock)));
        }

        const countLabel = optionsRow.querySelector(".version-options-header strong");
        if (countLabel) {
            countLabel.textContent = `${state.conditionStocks.length} condiciones`;
        }
        ensureConditionToggle(primaryRow, optionsRow);
    }

    function collapseToSimpleConditionStock(primaryRow, optionsRow, stock, state) {
        if (!primaryRow || !stock) {
            return;
        }

        applyConditionDataset(primaryRow, stock);
        applyPrimarySnapshot(primaryRow, state);
        optionsRow?.remove();

        const stockCell = primaryRow.children[0];
        const parentControls = stockCell?.querySelector(".parent-condition-stock-controls");
        const totalDisplay = stockCell?.querySelector(".stock-total-display");
        if (parentControls) {
            parentControls.hidden = false;
            setStockValue(parentControls.querySelector(".stock-value"), stock.quantity);
            parentControls.querySelectorAll(".stock-button").forEach(button => {
                button.dataset.row = stock.rowIndex || "0";
                button.dataset.condition = stock.condition || "";
                button.disabled = button.classList.contains("decrease") && (!stock.rowIndex || stock.rowIndex === "0");
                button.title = button.classList.contains("increase")
                        ? "Agregar una unidad"
                        : (stock.quantity <= 0 ? "Eliminar carta del Sheet" : "Registrar unidad vendida");
            });
        } else if (stockCell) {
            const controls = renderConditionOption(primaryRow, stock).querySelector(".stock-controls");
            controls.classList.remove("condition-stock-controls");
            stockCell.replaceChildren(controls);
        }
        if (totalDisplay) {
            totalDisplay.hidden = true;
        }

        const toggle = primaryRow.querySelector(".inventory-stock-toggle");
        toggle?.remove();

        const conditionCell = primaryRow.children[2];
        if (conditionCell) {
            const select = conditionCell.querySelector(".condition-price-select");
            if (select && stock.condition) {
                select.value = stock.condition;
                select.dispatchEvent(new Event("change"));
            } else {
                conditionCell.replaceChildren();
                const value = document.createElement("span");
                value.className = "condition-inline-value";
                value.textContent = stock.condition || "-";
                conditionCell.appendChild(value);
            }
        }

        const priceCells = primaryRow.querySelectorAll(".price-cell");
        const ckPrice = conditionPriceFromRow(primaryRow, stock, "ck");
        const localPrice = conditionPriceFromRow(primaryRow, stock, "local");
        if (priceCells[0]) {
            priceCells[0].textContent = ckPrice ? `$ ${ckPrice}` : "-";
        }
        if (priceCells[1]) {
            priceCells[1].textContent = localPrice ? `$ ${localPrice}` : "-";
        }

        setPrimaryReservationMode(primaryRow, false, stock);
    }

    function renderSimpleSnapshot(primaryRow, state, sourceButton = null) {
        const condition = sourceButton?.dataset?.condition
                || primaryRow.querySelector(".condition-price-select")?.value
                || primaryRow.querySelector(".condition-inline-value")?.textContent?.trim()
                || "NM";
        const stock = state.conditionStocks[0] || {
            condition,
            quantity: state.stockTotal,
            reservedQuantity: state.reservedQuantity,
            availableQuantity: state.availableQuantity,
            action: state.action,
            rowIndex: state.rowIndex,
            ckPriceUsd: "",
            localPrice: ""
        };
        collapseToSimpleConditionStock(primaryRow, null, stock, state);
    }

    function renderStockSnapshotForRow(primaryRow, snapshot, sourceButton = null) {
        if (!primaryRow || !snapshot) {
            return;
        }

        const state = normalizedStockSnapshot(snapshot);
        const optionsRow = primaryRow.nextElementSibling?.classList?.contains("inventory-stock-options-row")
                ? primaryRow.nextElementSibling
                : null;

        if (state.conditionStocks.length >= 2) {
            renderExpandedConditionStock(primaryRow, state);
            return;
        }

        if (optionsRow) {
            const snapshotRows = new Set(state.conditionStocks.map(stock => stock.rowIndex));
            const changedRow = state.rowIndex;
            let remainingStocks = Array.from(optionsRow.querySelectorAll(".inventory-condition-option"))
                    .map(option => {
                        const replacement = state.conditionStocks.find(stock => stock.rowIndex === String(option.dataset.row || "0"));
                        return replacement || stockFromConditionOption(option);
                    })
                    .filter(Boolean)
                    .filter(stock => {
                        if (snapshotRows.size > 0) {
                            return snapshotRows.has(stock.rowIndex);
                        }

                        const keep = stock.rowIndex !== changedRow;
                        if (!keep) {
                            const key = conditionDataKey(stock.condition);
                            if (key) {
                                primaryRow.dataset[`stock${key}`] = "0";
                                primaryRow.dataset[`available${key}`] = "0";
                                primaryRow.dataset[`row${key}`] = "0";
                            }
                        }
                        return keep;
                    });

            if (state.conditionStocks.length === 1 && !remainingStocks.some(stock => stock.rowIndex === state.conditionStocks[0].rowIndex)) {
                remainingStocks = state.conditionStocks;
            }

            if (remainingStocks.length >= 2) {
                renderExpandedConditionStock(primaryRow, {...state, conditionStocks: remainingStocks});
                return;
            }

            if (remainingStocks.length === 1) {
                collapseToSimpleConditionStock(primaryRow, optionsRow, remainingStocks[0], state);
                return;
            }

            optionsRow.remove();
        }

        renderSimpleSnapshot(primaryRow, state, sourceButton);
    }

    function applyStockSnapshotToRows(snapshot, sourceButton = null) {
        if (!snapshot || !snapshot.rowIndex || String(snapshot.rowIndex) === "0") {
            return;
        }

        const state = normalizedStockSnapshot(snapshot);
        const rows = new Set();
        const sourceRow = primaryInventoryRowFromButton(sourceButton);
        if (sourceRow && sourceRow.querySelector(`.stock-button[data-row="${state.rowIndex}"]`)) {
            rows.add(sourceRow);
        }

        [state.rowIndex, ...state.conditionStocks.map(stock => stock.rowIndex)].forEach(rowIndex => {
            document.querySelectorAll(`.stock-button[data-row="${rowIndex}"]`).forEach(button => {
                const row = primaryInventoryRowFromButton(button);
                if (row) {
                    rows.add(row);
                }
            });
        });

        rows.forEach(row => renderStockSnapshotForRow(row, snapshot, sourceButton));
        updateStockSummary();
    }

    function updateProductAggregate(productKey) {
        if (!productKey) {
            return;
        }

        const familyRows = Array.from(document.querySelectorAll(".product-family-row"))
                .filter(row => row.dataset.productKey === productKey);
        if (familyRows.length === 0) {
            return;
        }

        const total = familyRows.reduce((sum, row) => sum + (Number(row.dataset.stockTotal) || 0), 0);
        const reserved = familyRows.reduce((sum, row) => sum + (Number(row.dataset.reservedTotal) || 0), 0);
        const available = Math.max(total - reserved, 0);

        document.querySelectorAll(".product-aggregate-row").forEach(row => {
            if (row.dataset.productKey !== productKey) {
                return;
            }

            row.dataset.stockTotal = String(total);
            row.dataset.reservedTotal = String(reserved);
            row.dataset.availableTotal = String(available);
            row.dataset.inStock = String(available > 0);
            row.classList.toggle("in-stock", available > 0);

            const summary = row.querySelector(".product-stock-summary");
            if (summary) {
                summary.textContent = stockBreakdownText(total, reserved, available);
            }
        });
    }

    function updateProductPendingIndicator(sourceButton, pendingInfo) {
        const primaryRow = familyRowFromSource(sourceButton);
        const productKey = primaryRow?.dataset?.productKey || "";
        if (!productKey || !pendingInfo) {
            return;
        }

        document.querySelectorAll(".product-aggregate-row").forEach(row => {
            if (row.dataset.productKey !== productKey) {
                return;
            }

            const content = row.querySelector(".product-aggregate-content");
            if (!content) {
                return;
            }

            renderPendingSummary(content, pendingInfo);
        });
    }

    function renderPendingSummary(container, pendingInfo) {
        if (!container || !pendingInfo) {
            return;
        }

        const quantity = Math.max(Number(pendingInfo.quantity) || 0, 0);
        if (quantity <= 0) {
            container.querySelector(".product-pending-summary")?.remove();
            return;
        }

        let pending = container.querySelector(".product-pending-summary");
        if (!pending) {
            pending = document.createElement("small");
            pending.className = "product-pending-summary";
            container.appendChild(pending);
        }

        const clients = pendingInfo.clientsLabel || "cliente sin nombre";
        pending.textContent = pendingInfo.summaryLabel || (quantity === 1 ? "Pedido sin stock: 1" : `Pedidos sin stock: ${quantity}`);
        pending.title = pendingInfo.tooltip || `Carta pedida para ${clients}.`;
    }

    function updateFamilyPendingIndicator(sourceButton, pendingInfo) {
        const primaryRow = familyRowFromSource(sourceButton);
        const cell = primaryRow?.querySelector(".card-name-cell");
        renderPendingSummary(cell, pendingInfo);
    }

    function familyRowFromSource(sourceButton) {
        return primaryInventoryRowFromButton(sourceButton)
                || sourceButton?.closest?.(".product-family-row")
                || findFamilyRowByReservationIdentity(sourceButton?.dataset || {});
    }

    function findFamilyRowByReservationIdentity(identity) {
        const name = normalizedDatasetText(identity.name);
        const setCode = normalizedDatasetText(identity.setCode);
        const collectorNumber = normalizedDatasetText(identity.collectorNumber).replace(/^0+(?=\d)/, "");
        const printing = normalizedPrinting(identity.printing);
        if (!name) {
            return null;
        }

        return Array.from(document.querySelectorAll(".product-family-row")).find(row => {
            const rowCollector = normalizedDatasetText(row.dataset.collectorNumber).replace(/^0+(?=\d)/, "");
            return normalizedDatasetText(row.dataset.name) === name
                    && (!setCode || normalizedDatasetText(row.dataset.setCode) === setCode)
                    && (!collectorNumber || rowCollector === collectorNumber)
                    && (!printing || normalizedPrinting(row.dataset.printing) === printing);
        }) || null;
    }

    function normalizedDatasetText(value) {
        return String(value || "").trim().toLowerCase();
    }

    function normalizedPrinting(value) {
        const raw = normalizedDatasetText(value);
        if (!raw) {
            return "";
        }
        const text = raw.replace(/[\s_-]+/g, "");
        return text === "foil" ? "foil" : "nonfoil";
    }

    function updateConditionStockOption(option, stock) {
        if (!option || !stock) {
            return;
        }

        const quantity = Math.max(Number(stock.quantity) || 0, 0);
        const availableQuantity = Math.max(Number(stock.availableQuantity) || 0, 0);
        const reservedQuantity = Math.max(Number(stock.reservedQuantity) || 0, 0);
        const action = stock.action || stockActionForDisplay(quantity, reservedQuantity);
        option.dataset.stockQuantity = String(quantity);
        option.dataset.reservedQuantity = String(reservedQuantity);
        option.dataset.availableQuantity = String(availableQuantity);

        const summary = option.querySelector(".condition-stock-summary");
        if (summary) {
            summary.textContent = stockBreakdownText(quantity, reservedQuantity, availableQuantity);
        }

        const stockValue = option.querySelector(".stock-value");
        if (stockValue) {
            setStockValue(stockValue, quantity);
        }

        const status = option.querySelector(".stock-action-status");
        if (status) {
            status.textContent = action;
            status.classList.remove("con-stock", "en-stock", "sin-stock", "reservada");
            status.classList.add(action.toLowerCase().replace(/\s+/g, "-"));
        }

        const reservationButton = option.querySelector(".add-reservation-button");
        if (reservationButton) {
            reservationButton.dataset.stockQuantity = String(availableQuantity);
            reservationButton.dataset.row = String(stock.rowIndex || reservationButton.dataset.row || "0");
        }
    }

    function applyStockSnapshot(snapshot, sourceButton = null) {
        if (!snapshot || !snapshot.rowIndex || String(snapshot.rowIndex) === "0") {
            return;
        }

        applyStockSnapshotToRows(snapshot, sourceButton);
    }

    function reconcileConditionOptionsFromSnapshot(snapshot, sourceButton = null) {
        if (!snapshot || !Array.isArray(snapshot.conditionStocks)) {
            return false;
        }

        const triggerButton = sourceButton || document.querySelector(`.stock-button[data-row="${snapshot.rowIndex}"]`);
        const primaryRow = primaryInventoryRowFromButton(triggerButton);
        const optionsRow = primaryRow?.nextElementSibling?.classList?.contains("inventory-stock-options-row")
                ? primaryRow.nextElementSibling
                : triggerButton?.closest(".inventory-stock-options-row");
        if (!optionsRow) {
            return false;
        }

        const activeRows = new Set(
                snapshot.conditionStocks
                        .map(stock => String(stock.rowIndex || "0"))
                        .filter(rowIndex => rowIndex !== "0")
        );

        optionsRow.querySelectorAll(".inventory-condition-option").forEach(option => {
            const rowIndex = String(option.dataset.row || "0");
            if (activeRows.size > 0 && activeRows.has(rowIndex)) {
                return;
            }

            if (activeRows.size === 0 && rowIndex !== String(snapshot.rowIndex)) {
                return;
            }

            const removedCondition = option.dataset.condition || "";
            if (primaryRow && removedCondition) {
                const conditionKey = `${removedCondition.toLowerCase()[0].toUpperCase()}${removedCondition.toLowerCase().slice(1)}`;
                primaryRow.dataset[`stock${conditionKey}`] = "0";
                primaryRow.dataset[`available${conditionKey}`] = "0";
                primaryRow.dataset[`row${conditionKey}`] = "0";
            }
            option.remove();
        });

        const options = optionsRow.querySelectorAll(".inventory-condition-option");
        const count = options.length;
        const countLabel = optionsRow.querySelector(".version-options-header strong");
        if (countLabel) {
            countLabel.textContent = `${count} condicion${count === 1 ? "" : "es"}`;
        }

        if (count === 0) {
            optionsRow.remove();
            return true;
        }

        if (count === 1) {
            collapseConditionOptionsToPrimaryRow(primaryRow, optionsRow, options[0], snapshot);
            return true;
        }

        return true;
    }

    function collapseConditionOptionsToPrimaryRow(primaryRow, optionsRow, option, snapshot) {
        if (!primaryRow || !optionsRow || !option) {
            return;
        }

        const condition = option.dataset.condition || "";
        const quantity = Math.max(Number(option.dataset.stockQuantity) || 0, 0);
        const reserved = Math.max(Number(option.dataset.reservedQuantity) || 0, 0);
        const available = Math.max(Number(option.dataset.availableQuantity ?? option.dataset.stockQuantity) || 0, 0);
        const conditionKey = condition
                ? `${condition.toLowerCase()[0].toUpperCase()}${condition.toLowerCase().slice(1)}`
                : "";
        if (conditionKey) {
            primaryRow.dataset[`stock${conditionKey}`] = String(quantity);
            primaryRow.dataset[`available${conditionKey}`] = String(available);
            primaryRow.dataset[`row${conditionKey}`] = String(option.dataset.row || "0");
        }
        primaryRow.dataset.stockTotal = String(Math.max(Number(snapshot.stockTotal) || quantity, 0));
        primaryRow.dataset.reservedTotal = String(Math.max(Number(snapshot.reservedQuantity) || reserved, 0));
        primaryRow.dataset.availableTotal = String(Math.max(Number(snapshot.availableQuantity) || available, 0));
        primaryRow.dataset.inStock = String(available > 0);
        primaryRow.classList.toggle("in-stock", available > 0);

        const stockCell = primaryRow.children[0];
        const optionControls = option.querySelector(".stock-controls");
        if (stockCell && optionControls) {
            optionControls.classList.remove("condition-stock-controls");
            stockCell.replaceChildren(optionControls);
        }

        const toggle = primaryRow.querySelector(".inventory-stock-toggle");
        toggle?.remove();

        const conditionCell = primaryRow.children[2];
        if (conditionCell) {
            const select = conditionCell.querySelector(".condition-price-select");
            if (select && condition) {
                select.value = condition;
                select.dispatchEvent(new Event("change"));
            } else {
                conditionCell.replaceChildren();
                const value = document.createElement("span");
                value.className = "condition-inline-value";
                value.textContent = condition || "-";
                conditionCell.appendChild(value);
            }
        }

        const metaCells = option.querySelectorAll(".condition-meta-cell strong");
        const primaryPriceCells = primaryRow.querySelectorAll(".price-cell");
        if (metaCells[0] && primaryPriceCells[0]) {
            primaryPriceCells[0].textContent = metaCells[0].textContent || "-";
        }
        if (metaCells[1] && primaryPriceCells[1]) {
            primaryPriceCells[1].textContent = metaCells[1].textContent || "-";
        }

        const primaryStatus = primaryRow.querySelector(".stock-action-status");
        const optionStatus = option.querySelector(".stock-action-status");
        if (primaryStatus && optionStatus) {
            primaryStatus.textContent = optionStatus.textContent;
            primaryStatus.className = optionStatus.className;
        }

        const reservationCell = primaryRow.querySelector("td:last-child");
        const optionReservationButton = option.querySelector(".add-reservation-button");
        if (reservationCell && optionReservationButton && reservationCell.querySelector(".muted-cell")) {
            optionReservationButton.classList.remove("condition-reservation-button");
            reservationCell.replaceChildren(optionReservationButton);
        }

        applyStockBreakdown(
                primaryRow,
                stockBreakdownText(
                        Math.max(Number(snapshot.stockTotal) || quantity, 0),
                        Math.max(Number(snapshot.reservedQuantity) || reserved, 0),
                        Math.max(Number(snapshot.availableQuantity) || available, 0)
                )
        );
        updateProductAggregate(primaryRow.dataset.productKey);
        optionsRow.remove();
    }

    const pendingReservationDecision = (button) => new Promise(async resolve => {
        if (!pendingReservationModal || !pendingReservationList || !pendingReservationConfirm || !pendingReservationStock) {
            resolve({action: "stock"});
            return;
        }

        try {
            const selectedStock = selectedConditionStockState(button);
            const params = new URLSearchParams({
                name: button.dataset.name || "",
                setName: button.dataset.setName || "",
                setCode: button.dataset.setCode || "",
                collectorNumber: button.dataset.collectorNumber || "",
                printing: button.dataset.printing || "",
                condition: button.dataset.condition || "NM",
                rowIndex: button.dataset.row || "0"
            });
            const response = await fetch(`/api/reservas/pendientes?${params.toString()}`, {
                headers: {"Accept": "application/json"}
            });

            if (!response.ok) {
                if (selectedStock.reserved > 0 && selectedStock.available <= 0) {
                    showToast("No se pudo verificar la reserva activa para esta condicion", "error");
                    resolve({action: "cancel"});
                    return;
                }
                resolve({action: "stock"});
                return;
            }

            const reservations = await response.json();
            if (!reservations || reservations.length === 0) {
                if (selectedStock.reserved > 0 && selectedStock.available <= 0) {
                    showToast("Hay una reserva activa para esta condicion, pero no se pudo identificar el pedido", "error");
                    resolve({action: "cancel"});
                    return;
                }
                resolve({action: "stock"});
                return;
            }

            pendingReservationSelection = reservations[0];
            pendingReservationList.innerHTML = "";

            if (pendingReservationCardName) {
                pendingReservationCardName.textContent = [
                    button.dataset.name,
                    button.dataset.setName,
                    button.dataset.setCode && button.dataset.collectorNumber
                            ? `${button.dataset.setCode}-${button.dataset.collectorNumber}`
                            : "",
                    button.dataset.printing
                ].filter(Boolean).join(" | ");
            }

            reservations.forEach((reservation, index) => {
                const label = document.createElement("label");
                label.className = "pending-reservation-option";

                const input = document.createElement("input");
                input.type = "radio";
                input.name = "pendingReservation";
                input.value = reservation.id || "";
                input.checked = index === 0;

                input.addEventListener("change", () => {
                    pendingReservationSelection = reservation;
                });

                const detail = document.createElement("span");
                const client = document.createElement("strong");
                client.textContent = reservation.client || "Cliente";
                const meta = document.createElement("small");
                meta.textContent = `${reservation.phone || "Sin telefono"} | Pedido ${reservation.reservationDate || "sin fecha"} | Cant. ${reservation.quantity || "1"} | Retiro ${reservation.pickupDate || "A convenir"}`;
                detail.appendChild(client);
                detail.appendChild(meta);

                label.appendChild(input);
                label.appendChild(detail);
                pendingReservationList.appendChild(label);
            });

            const cleanup = () => {
                pendingReservationModal.hidden = true;
                pendingReservationModal.setAttribute("aria-hidden", "true");
                pendingReservationConfirm.onclick = null;
                pendingReservationStock.onclick = null;
                pendingReservationModal.querySelectorAll("[data-pending-reservation-cancel]").forEach(cancelButton => {
                    cancelButton.onclick = null;
                });
            };

            pendingReservationConfirm.onclick = () => {
                const selected = pendingReservationSelection || reservations[0];
                const remainingClients = reservations
                        .filter(reservation => String(reservation.id) !== String(selected.id))
                        .map(reservation => reservation.client)
                        .filter(Boolean);
                cleanup();
                resolve({action: "reservation", reservationId: selected.id, remainingClients});
            };

            pendingReservationStock.onclick = () => {
                cleanup();
                resolve({action: "stock"});
            };

            pendingReservationModal.querySelectorAll("[data-pending-reservation-cancel]").forEach(cancelButton => {
                cancelButton.onclick = () => {
                    cleanup();
                    resolve({action: "cancel"});
                };
            });

            pendingReservationModal.hidden = false;
            pendingReservationModal.setAttribute("aria-hidden", "false");
        } catch (error) {
            console.error(error);
            resolve({action: "stock"});
        }
    });

    const separatePendingReservation = async (button, reservationId, remainingClients = [], stockQuantity = null) => {
        showLoadingOverlay(
            null,
            "",
            "Separando pedido",
            "Marcando la carta como reservada y actualizando el stock..."
        );

        try {
            const response = await fetch("/reservas/separar", {
                method: "POST",
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded",
                    "Accept": "application/json"
                },
                body: new URLSearchParams({
                    reservationId,
                    sku: button.dataset.sku || "",
                    condition: button.dataset.condition || "NM",
                    rowIndex: button.dataset.row || "0",
                    stockQuantity: stockQuantity == null ? "-1" : String(stockQuantity)
                })
            });

            if (!response.ok) {
                showToast(await responseMessage(response, "No se pudo separar la reserva"), "error");
                return false;
            }

            const result = await response.json();
            const rowIndex = String(result.rowIndex || button.dataset.row || "");

            if (rowIndex && rowIndex !== "0") {
                button.dataset.row = rowIndex;

                const decreaseButton = button.parentElement.querySelector(".stock-button.decrease");
                if (decreaseButton) {
                    decreaseButton.dataset.row = rowIndex;
                    decreaseButton.disabled = false;
                }

                const row = button.closest("tr");
                const reservationButton = row ? row.querySelector(".add-reservation-button") : null;
                if (reservationButton) {
                    reservationButton.dataset.row = rowIndex;
                    reservationButton.dataset.stockQuantity = String(Math.max(Number(result.availableQuantity) || 0, 0));
                }

                syncStockRows(
                        rowIndex,
                        Number(result.stockQuantity) || 0,
                        result.action || "Reservada",
                        Number(result.availableQuantity),
                        Number(result.reservedQuantity),
                        result.snapshot,
                        button
                );
                updateProductPendingIndicator(button, result.pendingInfo || result.snapshot?.pendingInfo);
                updateFamilyPendingIndicator(button, result.familyPendingInfo || result.snapshot?.pendingInfo);
            }

            showToast(result.message || "Carta separada para reserva", "success");
            await refreshPickupAlerts();
            return true;
        } finally {
            hideLoadingOverlay(null, "");
        }
    };

    // ---- Actualiza stock ----
    document.addEventListener("click", async event => {
        const button = event.target.closest(".stock-button");
        if (!button) {
            return;
        }

                if (button.disabled) {
                    return;
                }

                button.disabled = true;
                try {

                    const rowIndex = button.dataset.row;

                    const increase =
                        button.classList.contains("increase");

                    const valueElement =
                        button.parentElement.querySelector(".stock-value");
                    const changeQuantity = stockChangeQuantity(valueElement);
                    const change = increase ? changeQuantity : -changeQuantity;

                    let currentValue =
                        parseInt(valueElement?.dataset?.stockValue || "", 10);

                    if (Number.isNaN(currentValue)) {
                        currentValue = displayedStockValue(valueElement);
                    }

                    if (increase) {
                        const pendingDecision = await pendingReservationDecision(button);

                        if (pendingDecision.action === "cancel") {
                            return;
                        }

                        if (pendingDecision.action === "reservation") {
                            let reservationRowIndex = rowIndex;
                            let reservationStockQuantity = Math.max(currentValue + changeQuantity, changeQuantity);
                            if (!reservationRowIndex || reservationRowIndex === "0") {
                                const sku = button.dataset.sku;
                                if (!sku) {
                                    showToast("No se pudo identificar la carta", "error");
                                    return;
                                }
                                await separatePendingReservation(
                                        button,
                                        pendingDecision.reservationId,
                                        pendingDecision.remainingClients || []
                                );
                                return;
                            } else {
                                const updateResponse = await fetch(
                                        "/inventory/quantity",
                                        {
                                            method: "POST",
                                            headers: {
                                                "Content-Type": "application/x-www-form-urlencoded",
                                                "Accept": "application/json"
                                            },
                                            body: new URLSearchParams({
                                                rowIndex: reservationRowIndex,
                                                change: changeQuantity,
                                                deleteWhenZero: false,
                                                reassignPending: false
                                            })
                                        }
                                );

                                if (!updateResponse.ok) {
                                    showToast(
                                            await responseMessage(updateResponse, "Error actualizando stock"),
                                            "error"
                                    );
                                    return;
                                }

                                const updateResult = await updateResponse.json();
                                reservationStockQuantity = Math.max(
                                        Number(updateResult.snapshot?.stockTotal ?? updateResult.stockQuantity) || reservationStockQuantity,
                                        reservationStockQuantity
                                );
                                syncStockRows(
                                        String(updateResult.rowIndex || reservationRowIndex),
                                        Number(updateResult.stockQuantity) || 0,
                                        updateResult.action || "",
                                        Number(updateResult.snapshot?.availableQuantity),
                                        Number(updateResult.snapshot?.reservedQuantity),
                                        updateResult.snapshot,
                                        button
                                );
                            }

                            await separatePendingReservation(
                                button,
                                pendingDecision.reservationId,
                                pendingDecision.remainingClients || [],
                                reservationStockQuantity
                            );
                            return;
                        }
                    }

                    if ((!rowIndex || rowIndex === "0") && !increase) {
                        showToast(
                            "Primero agrega esta impresion al Sheet",
                            "error"
                        );
                        return;
                    }

                    if ((!rowIndex || rowIndex === "0") && increase) {
                        const sku = button.dataset.sku;
                        const searchRowBeforeCreate = button.closest(".search-result-row");
                        const selectedConditionBeforeCreate = button.dataset.condition || "NM";
                        const selectedConditionKeyBeforeCreate =
                                `${selectedConditionBeforeCreate.toLowerCase()[0].toUpperCase()}${selectedConditionBeforeCreate.toLowerCase().slice(1)}`;
                        const shouldRefreshSearchRowAfterCreate = searchRowBeforeCreate
                                && Number(searchRowBeforeCreate.dataset[`stock${selectedConditionKeyBeforeCreate}`]) <= 0
                                && ["Nm", "Ex", "Vg", "G"].some(conditionKey =>
                                        conditionKey !== selectedConditionKeyBeforeCreate
                                        && Number(searchRowBeforeCreate.dataset[`stock${conditionKey}`]) > 0
                                );

                        if (!sku) {
                            showToast(
                                "No se pudo identificar la carta",
                                "error"
                            );
                            return;
                        }

                        const response = await fetch(
                            "/inventory/cards",
                            {
                                method: "POST",
                                headers: {
                                    "Content-Type":
                                        "application/x-www-form-urlencoded"
                                },
                                body:
                                    new URLSearchParams({
                                        sku,
                                        condition: button.dataset.condition || "NM",
                                        quantity: changeQuantity
                                    })
                            }
                        );

                        if (!response.ok) {
                            showToast(
                                await responseMessage(response, "Error agregando carta al Sheet"),
                                "error"
                            );
                            return;
                        }

                        const result = await response.json();
                        const newRowIndex = String(result.rowIndex || "");

                        if (newRowIndex) {
                            button.dataset.row = newRowIndex;
                            const selectedCondition = button.dataset.condition || "NM";
                            const conditionKey = `${selectedCondition.toLowerCase()[0].toUpperCase()}${selectedCondition.toLowerCase().slice(1)}`;

                            const decreaseButton =
                                button.parentElement.querySelector(".stock-button.decrease");

                            if (decreaseButton) {
                                decreaseButton.dataset.row = newRowIndex;
                                decreaseButton.dataset.condition = selectedCondition;
                                decreaseButton.disabled = false;
                                decreaseButton.title = "Registrar unidad vendida";
                            }

                            const row = button.closest(".search-result-row");
                            if (row) {
                                row.dataset[`row${conditionKey}`] = newRowIndex;
                                row.dataset[`stock${conditionKey}`] = String(result.snapshot?.stockTotal ?? changeQuantity);
                                row.dataset[`available${conditionKey}`] = String(result.snapshot?.availableQuantity ?? changeQuantity);
                            }
                        }

                        setStockValue(valueElement, result.snapshot?.stockTotal ?? changeQuantity);

                        const row = button.closest(".search-result-row");

                        if (row) {
                            row.dataset.inStock = "true";
                            row.classList.add("in-stock");

                            const reservationButton = row.querySelector(".add-reservation-button");
                            if (reservationButton) {
                                reservationButton.dataset.row = newRowIndex;
                                reservationButton.dataset.stockQuantity = String(result.snapshot?.availableQuantity ?? changeQuantity);
                            }
                        }
                        applyStockSnapshot(result.snapshot, button);

                        updateStockSummary();
                        button.title = "Agregar una unidad";

                        showToast(
                            stockChangeMessage(true, changeQuantity),
                            "success"
                        );

                        if (shouldRefreshSearchRowAfterCreate) {
                            window.setTimeout(() => {
                                window.location.reload();
                            }, 350);
                        }

                        return;
                    }

                    const deleteWhenZero = !increase && currentValue <= 0;

                    if (deleteWhenZero) {
                        const confirmed = await confirmStockDelete(
                            button.closest("tr")?.querySelector(".card-name-cell")?.textContent?.trim()
                        );

                        if (!confirmed) {
                            return;
                        }
                    }

                    const response = await fetch(
                        "/inventory/quantity",
                        {
                            method: "POST",
                            headers: {
                                "Content-Type":
                                    "application/x-www-form-urlencoded"
                            },
                            body:
                                new URLSearchParams({
                                    rowIndex,
                                    change,
                                    deleteWhenZero
                                })
                        }
                    );

                    if (!response.ok) {
                        showToast(
                            await responseMessage(response, "Error actualizando stock"),
                            "error"
                        );

                        return;
                    }

                    const result = await response.json();
                    const updatedRowIndex = String(result.rowIndex || rowIndex);

                    if (result.deleted) {
                        removeStockRows(updatedRowIndex);
                        showToast(
                            result.message || "Carta eliminada del Sheet",
                            "success"
                        );
                        return;
                    }

                    const updatedQuantity = Number.isFinite(Number(result.stockQuantity))
                            ? Number(result.stockQuantity)
                            : Math.max(currentValue + change, 0);
                    const updatedAction = result.action || (
                        updatedQuantity > 0 ? "En Stock" : "Sin Stock"
                    );

                    syncStockRows(
                            updatedRowIndex,
                            updatedQuantity,
                            updatedAction,
                            Number(result.snapshot?.availableQuantity),
                            Number(result.snapshot?.reservedQuantity),
                            result.snapshot,
                            button
                    );
                    const groupedRow = button.closest("tr");
                    if (!result.snapshot
                            && groupedRow?.querySelector(".inventory-stock-toggle")
                            && !button.closest(".inventory-condition-option")) {
                        const groupedQuantity = Math.max(currentValue + change, 0);
                        setStockValue(valueElement, groupedQuantity);
                        groupedRow?.querySelector(".add-reservation-button")
                                ?.setAttribute("data-stock-quantity", String(groupedQuantity));
                        groupedRow?.classList.toggle("in-stock", groupedQuantity > 0);
                        if (groupedRow) {
                            groupedRow.dataset.inStock = String(groupedQuantity > 0);
                        }
                        const groupedStatus = groupedRow?.querySelector(".stock-action-status");
                        if (groupedStatus && groupedQuantity > 0 && updatedAction === "Sin Stock") {
                            groupedStatus.textContent = "En Stock";
                            groupedStatus.classList.remove("sin-stock", "reservada");
                            groupedStatus.classList.add("en-stock");
                        }
                    }
                    showToast(
                        stockChangeMessage(increase, changeQuantity),
                        "success"
                    );

                } catch (e) {

                    console.error(e);

                    showToast(
                        "Error actualizando stock",
                        "error"
                    );

                } finally {

                    button.disabled = button.classList.contains("decrease")
                            && (!button.dataset.row || button.dataset.row === "0");
                }
    });

    function updateStockSummary() {
        if (!stockSummary) {
            return;
        }

        const total = Array.from(document.querySelectorAll(".stock-value"))
                .reduce((sum, element) => {
                    const value = displayedStockValue(element);
                    return sum + (Number.isNaN(value) ? 0 : value);
                }, 0);

        stockSummary.hidden = total <= 0;
        stockSummary.textContent = `STOCK LOCAL (${total})`;
    }

    function syncStockRows(rowIndex, quantity, action, availableQuantity, reservedQuantity, snapshot, sourceButton = null) {
        if (!rowIndex || rowIndex === "0") {
            return;
        }

        const backendSnapshot = snapshot && Number(snapshot.rowIndex) > 0 ? snapshot : null;
        const normalizedQuantity = backendSnapshot
                ? Math.max(Number(backendSnapshot.stockTotal) || 0, 0)
                : Math.max(Number(quantity) || 0, 0);
        const normalizedReserved = Number.isFinite(Number(reservedQuantity))
                ? Math.max(Number(reservedQuantity), 0)
                : (backendSnapshot ? Math.max(Number(backendSnapshot.reservedQuantity) || 0, 0) : (action === "Reservada" ? normalizedQuantity : 0));
        const normalizedAvailable = backendSnapshot
                ? Math.max(Number(backendSnapshot.availableQuantity) || 0, 0)
                : Number.isFinite(Number(availableQuantity))
                ? Math.max(Number(availableQuantity), 0)
                : Math.max(normalizedQuantity - normalizedReserved, 0);
        const normalizedAction = backendSnapshot?.action || action || stockActionForDisplay(normalizedQuantity, normalizedReserved);

        if (backendSnapshot) {
            applyStockSnapshot(backendSnapshot, sourceButton);
            return;
        }

        const conditionSnapshot = Array.isArray(backendSnapshot?.conditionStocks)
                ? backendSnapshot.conditionStocks.find(stock => String(stock.rowIndex || "0") === rowIndex)
                : null;
        const conditionWasRemoved = Array.isArray(backendSnapshot?.conditionStocks)
                && backendSnapshot.conditionStocks.length > 0
                && !conditionSnapshot;
        const rowQuantity = conditionSnapshot
                ? Math.max(Number(conditionSnapshot.quantity) || 0, 0)
                : conditionWasRemoved
                ? 0
                : normalizedQuantity;
        const rowReserved = conditionSnapshot
                ? Math.max(Number(conditionSnapshot.reservedQuantity) || 0, 0)
                : conditionWasRemoved
                ? 0
                : normalizedReserved;
        const rowAvailable = conditionSnapshot
                ? Math.max(Number(conditionSnapshot.availableQuantity) || 0, 0)
                : conditionWasRemoved
                ? 0
                : normalizedAvailable;
        const rowAction = conditionSnapshot?.action || normalizedAction;
        let reconciledConditionOptions = false;

        if (Array.isArray(backendSnapshot?.conditionStocks)) {
            reconciledConditionOptions = reconcileConditionOptionsFromSnapshot(backendSnapshot, sourceButton);
        }

        const relatedButtons = document.querySelectorAll(`.stock-button[data-row="${rowIndex}"]`);
        const relatedControls = new Set();

        if (Array.isArray(backendSnapshot?.conditionStocks) && backendSnapshot.conditionStocks.length > 0) {
            backendSnapshot.conditionStocks.forEach(stock => {
                const stockRowIndex = String(stock.rowIndex || "0");
                if (stockRowIndex === "0") {
                    return;
                }

                syncConditionStockOptions(
                        stockRowIndex,
                        Math.max(Number(stock.quantity) || 0, 0),
                        stock.action || stockActionForDisplay(stock.quantity, stock.reservedQuantity),
                        Math.max(Number(stock.availableQuantity) || 0, 0),
                        Math.max(Number(stock.reservedQuantity) || 0, 0)
                );
            });
        } else if (!reconciledConditionOptions) {
            syncConditionStockOptions(rowIndex, rowQuantity, rowAction, rowAvailable, rowReserved);
        }

        relatedButtons.forEach(relatedButton => {
            relatedControls.add(relatedButton.closest(".stock-controls"));
            relatedButton.dataset.row = rowIndex;

            if (relatedButton.classList.contains("decrease")) {
                relatedButton.disabled = false;
                relatedButton.title = rowQuantity <= 0
                        ? "Eliminar carta del Sheet"
                        : "Registrar unidad vendida";
            }

            if (relatedButton.classList.contains("increase")) {
                relatedButton.disabled = false;
                relatedButton.title = "Agregar una unidad";
            }
        });

        relatedControls.forEach(control => {
            if (!control) {
                return;
            }

            const value = control.querySelector(".stock-value");

            if (value) {
                setStockValue(value, rowQuantity);
            }

            if (control.closest(".inventory-condition-option")) {
                return;
            }

            const row = control.closest("tr");

            if (row) {
                const condition = control.querySelector(".stock-button.increase")?.dataset.condition || "NM";
                const conditionKey = `${condition.toLowerCase()[0].toUpperCase()}${condition.toLowerCase().slice(1)}`;
                row.dataset[`stock${conditionKey}`] = String(rowQuantity);
                row.dataset[`available${conditionKey}`] = String(rowAvailable);
                row.dataset[`row${conditionKey}`] = rowIndex;
                row.dataset.stockTotal = String(normalizedQuantity);
                row.dataset.reservedTotal = String(normalizedReserved);
                row.dataset.availableTotal = String(normalizedAvailable);
                const inStock = ["nm", "ex", "vg", "g"]
                        .some(item => Number(row.dataset[`available${item[0].toUpperCase()}${item.slice(1)}`] ?? row.dataset[`stock${item[0].toUpperCase()}${item.slice(1)}`]) > 0);
                row.dataset.inStock = String(inStock);
                row.classList.toggle("in-stock", inStock);

                const status = row.querySelector(".stock-action-status");

                if (status) {
                    status.textContent = normalizedAction;
                    status.classList.remove("con-stock", "en-stock", "sin-stock", "reservada");
                    status.classList.add(
                        normalizedAction.toLowerCase().replace(/\s+/g, "-")
                    );
                }

                const reservationButton = row.querySelector(".add-reservation-button");
                if (reservationButton) {
                    reservationButton.dataset.stockQuantity = String(rowAvailable);
                }

                if (!inStock) {
                    const table = row.closest("table");
                    const activeStockFilter = table
                            ? table.querySelector(".stock-filter-button[aria-pressed='true']")
                            : null;

                    if (activeStockFilter) {
                        row.hidden = true;
                    }
                } else {
                    row.hidden = false;
                }

                updateProductAggregate(row.dataset.productKey);
            }
        });

        updateStockSummary();
    }

    function syncConditionStockOptions(rowIndex, quantity, action, availableQuantity, reservedQuantity) {
        document.querySelectorAll(`.inventory-condition-option[data-row="${rowIndex}"]`).forEach(option => {
            option.dataset.stockQuantity = String(quantity);
            option.dataset.reservedQuantity = String(reservedQuantity);
            option.dataset.availableQuantity = String(availableQuantity);

            const summary = option.querySelector(".condition-stock-summary");
            if (summary) {
                summary.textContent = stockBreakdownText(quantity, reservedQuantity, availableQuantity);
            }

            const stockValue = option.querySelector(".stock-value");
            if (stockValue) {
                setStockValue(stockValue, quantity);
            }

            const status = option.querySelector(".stock-action-status");
            if (status) {
                status.textContent = action;
                status.classList.remove("con-stock", "en-stock", "sin-stock", "reservada");
                status.classList.add(action.toLowerCase().replace(/\s+/g, "-"));
            }

            const reservationButton = option.querySelector(".add-reservation-button");
            if (reservationButton) {
                reservationButton.dataset.stockQuantity = String(availableQuantity);
                reservationButton.dataset.row = rowIndex;
            }

            const optionsRow = option.closest(".inventory-stock-options-row");
            const primaryRow = optionsRow?.previousElementSibling;
            if (primaryRow) {
                const condition = option.dataset.condition || "";
                const conditionKey = conditionDataKey(condition);
                if (conditionKey) {
                    primaryRow.dataset[`stock${conditionKey}`] = String(quantity);
                    primaryRow.dataset[`reserved${conditionKey}`] = String(reservedQuantity);
                    primaryRow.dataset[`available${conditionKey}`] = String(availableQuantity);
                    primaryRow.dataset[`row${conditionKey}`] = rowIndex;
                }

                const total = Array.from(optionsRow.querySelectorAll(".inventory-condition-option"))
                        .reduce((sum, item) => sum + (Number(item.dataset.stockQuantity) || 0), 0);
                const reservedTotal = Array.from(optionsRow.querySelectorAll(".inventory-condition-option"))
                        .reduce((sum, item) => sum + (Number(item.dataset.reservedQuantity) || 0), 0);
                const availableTotal = Math.max(total - reservedTotal, 0);
                const stockTotal = primaryRow.querySelector(".stock-total-display, .stock-value");
                if (stockTotal) {
                    setStockValue(stockTotal, total);
                }

                const select = primaryRow.querySelector(".condition-price-select");
                if (select && (!condition || select.value === condition)) {
                    select.dispatchEvent(new Event("change"));
                }
                primaryRow.dataset.inStock = String(availableTotal > 0);
                primaryRow.dataset.stockTotal = String(total);
                primaryRow.dataset.reservedTotal = String(reservedTotal);
                primaryRow.dataset.availableTotal = String(availableTotal);
                primaryRow.classList.toggle("in-stock", availableTotal > 0);
                updateProductAggregate(primaryRow.dataset.productKey);
            }
        });
    }

    function removeStockRows(rowIndex) {
        if (!rowIndex || rowIndex === "0") {
            return;
        }

        const removedIndex = Number(rowIndex);
        const relatedButtons = document.querySelectorAll(`.stock-button[data-row="${rowIndex}"]`);
        const rowsToRemove = new Set();

        relatedButtons.forEach(button => {
            const row = button.closest("tr");

            if (row) {
                if (row.classList.contains("search-result-row")) {
                    const condition = button.dataset.condition || "NM";
                    const conditionKey = `${condition.toLowerCase()[0].toUpperCase()}${condition.toLowerCase().slice(1)}`;
                    row.dataset[`stock${conditionKey}`] = "0";
                    row.dataset[`reserved${conditionKey}`] = "0";
                    row.dataset[`available${conditionKey}`] = "0";
                    row.dataset[`row${conditionKey}`] = "0";
                    button.dataset.row = "0";

                    if (button.classList.contains("decrease")) {
                        button.disabled = true;
                    }

                    const select = row.querySelector(".condition-price-select");
                    const stockValue = row.querySelector(".stock-value");
                    const reservationButton = row.querySelector(".add-reservation-button");
                    const rowCondition = select?.value || row.querySelector(".condition-inline-value")?.textContent?.trim() || button.dataset.condition || "NM";
                    const matchesDisplayedCondition = rowCondition === condition;
                    if (matchesDisplayedCondition && stockValue) {
                        setStockValue(stockValue, 0);
                    }

                    if (matchesDisplayedCondition && reservationButton) {
                        reservationButton.dataset.row = "0";
                        reservationButton.dataset.stockQuantity = "0";
                    }

                    const inStock = ["nm", "ex", "vg", "g"]
                            .some(item => Number(row.dataset[`stock${item[0].toUpperCase()}${item.slice(1)}`]) > 0);
                    row.dataset.inStock = String(inStock);
                    row.classList.toggle("in-stock", inStock);
                } else {
                    rowsToRemove.add(row);
                }
            }
        });

        rowsToRemove.forEach(row => row.remove());

        document.querySelectorAll(".stock-button[data-row]").forEach(button => {
            const currentIndex = Number(button.dataset.row);

            if (Number.isFinite(currentIndex) && currentIndex > removedIndex) {
                button.dataset.row = String(currentIndex - 1);
            }
        });

        document.querySelectorAll(".search-result-row").forEach(row => {
            ["Nm", "Ex", "Vg", "G"].forEach(conditionKey => {
                const currentIndex = Number(row.dataset[`row${conditionKey}`]);
                if (Number.isFinite(currentIndex) && currentIndex > removedIndex) {
                    row.dataset[`row${conditionKey}`] = String(currentIndex - 1);
                }
            });
        });

        updateStockSummary();
    }

    function confirmStockDelete(cardName) {
        if (!stockDeleteDialog) {
            return Promise.resolve(window.confirm(
                "Esta carta ya esta en 0. Si continuas, tambien se va a eliminar del Sheet."
            ));
        }

        const title = stockDeleteDialog.querySelector("[data-stock-delete-title]");
        const closeButtons = stockDeleteDialog.querySelectorAll("[data-stock-delete-cancel]");
        const confirmButton = stockDeleteDialog.querySelector("[data-stock-delete-confirm]");

        if (title) {
            title.textContent = cardName
                    ? `Eliminar "${cardName}" del Sheet`
                    : "Eliminar carta del Sheet";
        }

        stockDeleteDialog.hidden = false;
        stockDeleteDialog.setAttribute("aria-hidden", "false");

        return new Promise(resolve => {
            let resolved = false;

            const finish = value => {
                if (resolved) {
                    return;
                }

                resolved = true;
                stockDeleteDialog.hidden = true;
                stockDeleteDialog.setAttribute("aria-hidden", "true");
                confirmButton?.removeEventListener("click", confirm);
                closeButtons.forEach(button => button.removeEventListener("click", cancel));
                stockDeleteDialog.removeEventListener("click", backdropCancel);
                document.removeEventListener("keydown", escapeCancel);
                resolve(value);
            };

            const confirm = () => finish(true);
            const cancel = () => finish(false);
            const backdropCancel = event => {
                if (event.target === stockDeleteDialog) {
                    finish(false);
                }
            };
            const escapeCancel = event => {
                if (event.key === "Escape") {
                    finish(false);
                }
            };

            confirmButton?.addEventListener("click", confirm);
            closeButtons.forEach(button => button.addEventListener("click", cancel));
            stockDeleteDialog.addEventListener("click", backdropCancel);
            document.addEventListener("keydown", escapeCancel);
            confirmButton?.focus();
        });
    }

// ---- Toast ----
    function showToast(message, type) {
        const existingToast = document.querySelector(".toast");

        if (existingToast) {
            existingToast.remove();
        }

        const toast = document.createElement("div");

        toast.className = `toast ${type}`;
        toast.textContent = message;
        document.body.appendChild(toast);

        setTimeout(() => {toast.remove();}, 2400);
    }
});

