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

    const showLoadingOverlay = (form, buttonText, title, message) => {
        if (!form || !loadingOverlay) {
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

        const button = form.querySelector("button[type='submit']");
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
        importConfirmForm.addEventListener("submit", () => {
            showLoadingOverlay(
                importConfirmForm,
                "Añadiendo...",
                "Añadiendo al stock",
                "Guardando las cartas seleccionadas en Google Sheet..."
            );
        });
    }

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

            suggestionTimer = setTimeout(() => fetchSuggestions(value), 50);
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
                                        sku
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

                            const decreaseButton =
                                button.parentElement.querySelector(".stock-button.decrease");

                            if (decreaseButton) {
                                decreaseButton.dataset.row = newRowIndex;
                                decreaseButton.disabled = false;
                                decreaseButton.title = "Registrar unidad vendida";
                            }
                        }

                        valueElement.textContent = "1";

                        const row = button.closest(".search-result-row");

                        if (row) {
                            row.dataset.inStock = "true";
                            row.classList.add("in-stock");
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
                        updatedQuantity > 0 ? "CON STOCK" : "SIN STOCK"
                    );

                    syncStockRows(updatedRowIndex, updatedQuantity, updatedAction);
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

                    button.disabled = false;
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
            normalizedQuantity > 0 ? "CON STOCK" : "SIN STOCK"
        );
        const relatedButtons = document.querySelectorAll(`.stock-button[data-row="${rowIndex}"]`);
        const relatedControls = new Set();

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

            const row = control.closest("tr");

            if (row) {
                const inStock = normalizedQuantity > 0;
                row.dataset.inStock = String(inStock);
                row.classList.toggle("in-stock", inStock);

                const status = row.querySelector(".stock-action-status");

                if (status) {
                    status.textContent = normalizedAction;
                    status.classList.remove("con-stock", "sin-stock");
                    status.classList.add(
                        normalizedAction.toLowerCase().replace(/\s+/g, "-")
                    );
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

    function removeStockRows(rowIndex) {
        if (!rowIndex || rowIndex === "0") {
            return;
        }

        const removedIndex = Number(rowIndex);
        const relatedButtons = document.querySelectorAll(`.stock-button[data-row="${rowIndex}"]`);
        const rows = new Set();

        relatedButtons.forEach(button => {
            const row = button.closest("tr");

            if (row) {
                rows.add(row);
            }
        });

        rows.forEach(row => row.remove());

        document.querySelectorAll(".stock-button[data-row]").forEach(button => {
            const currentIndex = Number(button.dataset.row);

            if (Number.isFinite(currentIndex) && currentIndex > removedIndex) {
                button.dataset.row = String(currentIndex - 1);
            }
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

