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
                    "Añadiendo...",
                    "Añadiendo al stock",
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
                    (reservationsByKey[key] || []).forEach(reservation => {
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
                        confirmMessage: `Hay ${pendingCount} reserva(s) pendiente(s) entre las cartas seleccionadas. ¿Queres separar esas unidades para reservas antes de sumar stock?`
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
                "Añadiendo...",
                "Añadiendo al stock",
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

    const movementDateInputs = document.querySelectorAll(".movement-date-field input[type='date']");

    movementDateInputs.forEach(movementDateInput => {
        const openMovementDatePicker = event => {
            if (typeof movementDateInput.showPicker !== "function") {
                return;
            }

            try {
                event?.preventDefault();
                movementDateInput.focus();
                movementDateInput.showPicker();
            } catch (e) {
                // El navegador puede bloquearlo si no lo considera una acción directa del usuario.
            }
        };

        movementDateInput.addEventListener("pointerdown", openMovementDatePicker);
        movementDateInput.addEventListener("mousedown", openMovementDatePicker);
        movementDateInput.addEventListener("keydown", event => {
            if (event.key === "Enter" || event.key === " ") {
                openMovementDatePicker(event);
            }
        });
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
            const ckCell = row.querySelector(".ck-price-cell");
            const localCell = row.querySelector(".local-price-cell");
            const stockValue = row.querySelector(".stock-value");
            const increaseButton = row.querySelector(".stock-button.increase");
            const decreaseButton = row.querySelector(".stock-button.decrease");
            const reservationButton = row.querySelector(".add-reservation-button");

            if (ckCell) {
                ckCell.textContent = ckPrice ? `$ ${ckPrice}` : "-";
            }

            if (localCell) {
                localCell.textContent = localPrice ? `$ ${localPrice}` : "-";
            }

            if (stockValue) {
                stockValue.textContent = String(stockQuantity);
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
                reservationButton.dataset.stockQuantity = String(stockQuantity);
            }

            const hasAnyStock = ["nm", "ex", "vg", "g"].some(item => Number(row.dataset[`stock${item[0].toUpperCase()}${item.slice(1)}`]) > 0);
            row.dataset.inStock = hasAnyStock ? "true" : "false";
            row.classList.toggle("in-stock", hasAnyStock);
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

        const normalizeClientValue = value => (value || "").trim().toLowerCase();
        const digitsOnly = value => (value || "").replace(/\D/g, "");

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
                option.value = client.client;
                option.label = client.phone
                        ? `${client.client} | ${client.phone}`
                        : client.client;
                reservationClientOptions.appendChild(option);
            });
        };

        const upsertReservationClientOption = client => {
            if (!client || !client.client) {
                return;
            }

            const normalizedClient = normalizeClientValue(client.client);
            const existingIndex = reservationClients.findIndex(
                    existing => normalizeClientValue(existing.client) === normalizedClient
            );
            const nextClient = {
                client: client.client,
                phone: client.phone || "",
                dni: client.dni || ""
            };

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
            const value = normalizeClientValue(clientInput?.value);

            if (!value) {
                return null;
            }

            return reservationClients.find(client => normalizeClientValue(client.client) === value) || null;
        };

        const applySelectedReservationClient = () => {
            const client = selectedReservationClient();
            if (!client) {
                return;
            }

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

        restrictNumericField(reservationModalForm.elements.phone, 10);
        restrictNumericField(reservationModalForm.elements.dni, 8);

        addReservationButtons.forEach(button => {
            button.addEventListener("click", async () => {
                activeReservationButton = button;
                const stockQuantity = Number(button.dataset.stockQuantity || 0);
                await loadReservationClients();
                setReservationValue("status", "Sin Stock");
                setReservationValue("name", button.dataset.name);
                setReservationValue("setName", button.dataset.setName);
                setReservationValue("setCode", button.dataset.setCode);
                setReservationValue("collectorNumber", button.dataset.collectorNumber);
                setReservationValue("printing", button.dataset.printing);
                setReservationValue("rowIndex", button.dataset.row);
                setReservationValue("quantity", "1");
                setReservationValue("pickupDate", "A convenir");

                if (removeFromStockControl) {
                    const checkbox = removeFromStockControl.querySelector("input[type='checkbox']");
                    const canReserveFromStock = stockQuantity > 0 && button.dataset.row && button.dataset.row !== "0";
                    removeFromStockControl.hidden = !canReserveFromStock;
                    if (checkbox) {
                        checkbox.checked = canReserveFromStock;
                    }
                }

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
        });

        reservationModalForm.elements.client?.addEventListener("input", applySelectedReservationClient);
        reservationModalForm.elements.client?.addEventListener("change", applySelectedReservationClient);

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
                    syncStockRows(String(result.rowIndex), Number(result.stockQuantity), result.action || "");
                    if (result.action === "Reservada") {
                        updatePendingStockInfoForButton(activeReservationButton, []);
                    }
                } else if (activeReservationButton) {
                    updatePendingStockInfoForButton(
                        activeReservationButton,
                        [
                            ...pendingClientsFromRow(primaryInventoryRowFromButton(activeReservationButton)),
                            reservationModalForm.elements.client?.value || "cliente"
                        ]
                    );
                }

                upsertReservationClientOption({
                    client: result.client || reservationModalForm.elements.client?.value,
                    phone: result.phone || reservationModalForm.elements.phone?.value,
                    dni: result.dni || reservationModalForm.elements.dni?.value
                });

                showToast(result.message || "Pedido guardado en Reservas", "success");
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

    stockFilterButtons.forEach(stockFilterButton => {
        const table = stockFilterButton.closest("table");
        const stockFilterRows = table
                ? table.querySelectorAll(".search-result-row, .stock-filter-row")
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

    const credentialsInput = document.getElementById("googleCredentials");
    const credentialsFileName = document.getElementById("credentials-file-name");

    if (credentialsInput && credentialsFileName) {
        credentialsInput.addEventListener("change", () => {
            const file = credentialsInput.files[0];
            credentialsFileName.textContent = file
                ? file.name
                : "Archivo .json privado que te pasan por fuera de GitHub";
        });
    }

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

    document.querySelectorAll(".version-toggle").forEach(button => {
        button.addEventListener("click", () => {
            const optionsRow = document.getElementById(button.getAttribute("aria-controls"));

            if (!optionsRow) {
                return;
            }

            const shouldOpen = optionsRow.hidden;
            optionsRow.hidden = !shouldOpen;
            button.setAttribute("aria-expanded", String(shouldOpen));
            updateImportSelectionState();
        });
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

    function updatePendingStockInfo(row, clients) {
        if (!row) {
            return;
        }

        const status = row.querySelector(".stock-action-status");
        if (!status) {
            return;
        }

        const cleanClients = Array.from(new Set((clients || [])
                .map(client => String(client || "").trim())
                .filter(Boolean)));
        let note = status.parentElement.querySelector(".pending-stock-info");

        if (cleanClients.length === 0) {
            note?.remove();
            return;
        }

        if (!note) {
            note = document.createElement("span");
            note.className = "pending-stock-info";
            note.setAttribute("aria-label", "Carta pedida");
            note.textContent = "i";
            status.insertAdjacentElement("afterend", note);
        }

        note.title = `Carta pedida para ${cleanClients.join(", ")}.`;
        note.dataset.clients = cleanClients.join("|");
    }

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

    function updatePendingStockInfoForButton(button, clients) {
        const primaryRow = primaryInventoryRowFromButton(button);
        updatePendingStockInfo(primaryRow, clients);

        const currentRow = button?.closest("tr");
        if (currentRow && currentRow !== primaryRow) {
            updatePendingStockInfo(currentRow, clients);
        }
    }

    function pendingClientsFromRow(row) {
        const note = row?.querySelector(".pending-stock-info");
        if (!note) {
            return [];
        }

        if (note.dataset.clients) {
            return note.dataset.clients.split("|").map(client => client.trim()).filter(Boolean);
        }

        return note.title
                .replace(/^Carta pedida para\s+/i, "")
                .replace(/\.$/, "")
                .split(",")
                .map(client => client.trim())
                .filter(Boolean);
    }

    const pendingReservationDecision = (button) => new Promise(async resolve => {
        if (!pendingReservationModal || !pendingReservationList || !pendingReservationConfirm || !pendingReservationStock) {
            resolve({action: "stock"});
            return;
        }

        try {
            const params = new URLSearchParams({
                name: button.dataset.name || "",
                setName: button.dataset.setName || "",
                setCode: button.dataset.setCode || "",
                collectorNumber: button.dataset.collectorNumber || "",
                printing: button.dataset.printing || ""
            });
            const response = await fetch(`/api/reservas/pendientes?${params.toString()}`, {
                headers: {"Accept": "application/json"}
            });

            if (!response.ok) {
                resolve({action: "stock"});
                return;
            }

            const reservations = await response.json();
            if (!reservations || reservations.length === 0) {
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

    const separatePendingReservation = async (button, reservationId, remainingClients = []) => {
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
                    rowIndex: button.dataset.row || "0"
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
                    reservationButton.dataset.stockQuantity = String(Math.max(Number(result.stockQuantity) || 0, 0));
                }

                syncStockRows(rowIndex, Number(result.stockQuantity) || 0, result.action || "Reservada");
            }

            const row = primaryInventoryRowFromButton(button);
            const fallbackClients = pendingClientsFromRow(row)
                    .filter(client => !(result.client && client === result.client));
            updatePendingStockInfoForButton(button, remainingClients.length > 0 ? remainingClients : fallbackClients);

            showToast(result.message || "Carta separada para reserva", "success");
            return true;
        } finally {
            hideLoadingOverlay(null, "");
        }
    };

    // ---- Actualiza stock ----
    const stockButtons = document.querySelectorAll(".stock-button");

    if (stockButtons.length > 0) {

        stockButtons.forEach(button => {

            button.addEventListener("click", async () => {

                if (button.disabled) {
                    return;
                }

                button.disabled = true;
                try {

                    const rowIndex = button.dataset.row;

                    const increase =
                        button.classList.contains("increase");

                    const change = increase ? 1 : -1;

                    const valueElement =
                        button.parentElement.querySelector(".stock-value");

                    let currentValue =
                        parseInt(valueElement.textContent, 10);

                    if (Number.isNaN(currentValue)) {
                        currentValue = 0;
                    }

                    if (increase) {
                        const pendingDecision = await pendingReservationDecision(button);

                        if (pendingDecision.action === "cancel") {
                            return;
                        }

                        if (pendingDecision.action === "reservation") {
                            await separatePendingReservation(
                                button,
                                pendingDecision.reservationId,
                                pendingDecision.remainingClients || []
                            );
                            return;
                        }
                    }

                    if ((!rowIndex || rowIndex === "0") && !increase) {
                        showToast(
                            "Primero agregá esta impresión al Sheet",
                            "error"
                        );
                        return;
                    }

                    if ((!rowIndex || rowIndex === "0") && increase) {
                        const sku = button.dataset.sku;

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
                                        condition: button.dataset.condition || "NM"
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
                                row.dataset[`stock${conditionKey}`] = "1";
                            }
                        }

                        valueElement.textContent = "1";

                        const row = button.closest(".search-result-row");

                        if (row) {
                            row.dataset.inStock = "true";
                            row.classList.add("in-stock");

                            const reservationButton = row.querySelector(".add-reservation-button");
                            if (reservationButton) {
                                reservationButton.dataset.row = newRowIndex;
                                reservationButton.dataset.stockQuantity = "1";
                            }
                        }

                        updateStockSummary();
                        button.title = "Agregar una unidad";

                        showToast(
                            "Carta agregada al Sheet",
                            "success"
                        );

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

                    syncStockRows(updatedRowIndex, updatedQuantity, updatedAction);
                    const groupedRow = button.closest("tr");
                    if (groupedRow?.querySelector(".inventory-stock-toggle")
                            && !button.closest(".inventory-condition-option")) {
                        const groupedQuantity = Math.max(currentValue + change, 0);
                        valueElement.textContent = String(groupedQuantity);
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
                        result.message || (
                            increase
                                ? "Unidad agregada"
                                : "Unidad vendida"
                        ),
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
        });
    }

    function updateStockSummary() {
        if (!stockSummary) {
            return;
        }

        const total = Array.from(document.querySelectorAll(".stock-value"))
                .reduce((sum, element) => {
                    const value = parseInt(element.textContent, 10);
                    return sum + (Number.isNaN(value) ? 0 : value);
                }, 0);

        stockSummary.hidden = total <= 0;
        stockSummary.textContent = `STOCK LOCAL (${total})`;
    }

    function syncStockRows(rowIndex, quantity, action) {
        if (!rowIndex || rowIndex === "0") {
            return;
        }

        const normalizedQuantity = Math.max(Number(quantity) || 0, 0);
        const normalizedAction = action || (
            normalizedQuantity > 0 ? "En Stock" : "Sin Stock"
        );
        const relatedButtons = document.querySelectorAll(`.stock-button[data-row="${rowIndex}"]`);
        const relatedControls = new Set();
        syncConditionStockOptions(rowIndex, normalizedQuantity, normalizedAction);

        relatedButtons.forEach(relatedButton => {
            relatedControls.add(relatedButton.closest(".stock-controls"));
            relatedButton.dataset.row = rowIndex;

            if (relatedButton.classList.contains("decrease")) {
                relatedButton.disabled = false;
                relatedButton.title = normalizedQuantity <= 0
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
                value.textContent = String(normalizedQuantity);
            }

            if (control.closest(".inventory-condition-option")) {
                return;
            }

            const row = control.closest("tr");

            if (row) {
                const condition = control.querySelector(".stock-button.increase")?.dataset.condition || "NM";
                const conditionKey = `${condition.toLowerCase()[0].toUpperCase()}${condition.toLowerCase().slice(1)}`;
                row.dataset[`stock${conditionKey}`] = String(normalizedQuantity);
                row.dataset[`row${conditionKey}`] = rowIndex;
                const inStock = ["nm", "ex", "vg", "g"]
                        .some(item => Number(row.dataset[`stock${item[0].toUpperCase()}${item.slice(1)}`]) > 0);
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
                    reservationButton.dataset.stockQuantity = String(normalizedQuantity);
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
            }
        });

        updateStockSummary();
    }

    function syncConditionStockOptions(rowIndex, quantity, action) {
        document.querySelectorAll(`.inventory-condition-option[data-row="${rowIndex}"]`).forEach(option => {
            option.dataset.stockQuantity = String(quantity);

            const summary = option.querySelector(".condition-stock-summary");
            if (summary) {
                summary.textContent = action === "Reservada"
                        ? `${quantity} total | ${quantity} reservadas`
                        : `${quantity} total | ${quantity} disponibles`;
            }

            const stockValue = option.querySelector(".stock-value");
            if (stockValue) {
                stockValue.textContent = String(quantity);
            }

            const status = option.querySelector(".stock-action-status");
            if (status) {
                status.textContent = action;
                status.classList.remove("con-stock", "en-stock", "sin-stock", "reservada");
                status.classList.add(action.toLowerCase().replace(/\s+/g, "-"));
            }

            const reservationButton = option.querySelector(".add-reservation-button");
            if (reservationButton) {
                reservationButton.dataset.stockQuantity = String(quantity);
                reservationButton.dataset.row = rowIndex;
            }

            const optionsRow = option.closest(".inventory-stock-options-row");
            const primaryRow = optionsRow?.previousElementSibling;
            if (primaryRow) {
                const total = Array.from(optionsRow.querySelectorAll(".inventory-condition-option"))
                        .reduce((sum, item) => sum + (Number(item.dataset.stockQuantity) || 0), 0);
                const stockTotal = primaryRow.querySelector(".stock-total-display, .stock-value");
                if (stockTotal) {
                    stockTotal.textContent = String(total);
                }
                const primaryStatus = primaryRow.querySelector(".stock-action-status");
                if (primaryStatus && total > 0 && primaryStatus.textContent.trim() === "Sin Stock") {
                    primaryStatus.textContent = "En Stock";
                    primaryStatus.classList.remove("sin-stock", "reservada");
                    primaryStatus.classList.add("en-stock");
                }
                primaryRow.dataset.inStock = String(total > 0);
                primaryRow.classList.toggle("in-stock", total > 0);
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
                    row.dataset[`row${conditionKey}`] = "0";
                    button.dataset.row = "0";

                    if (button.classList.contains("decrease")) {
                        button.disabled = true;
                    }

                    const select = row.querySelector(".condition-price-select");
                    const stockValue = row.querySelector(".stock-value");
                    const reservationButton = row.querySelector(".add-reservation-button");
                    if (select?.value === condition && stockValue) {
                        stockValue.textContent = "0";
                    }

                    if (select?.value === condition && reservationButton) {
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

