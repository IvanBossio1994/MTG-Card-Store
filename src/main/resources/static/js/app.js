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
        let activeSuggestionIndex = -1;

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
            try {
                const response = await fetch(`/api/cartas/sugerencias?q=${encodeURIComponent(value)}`);

                if (!response.ok) {
                    closeSuggestions();
                    return;
                }

                renderSuggestions(await response.json());
            } catch (e) {
                closeSuggestions();
            }
        };

        searchInput.addEventListener("input", () => {
            const value = searchInput.value.trim();
            clearTimeout(suggestionTimer);

            if (value.length < 3) {
                closeSuggestions();
                return;
            }

            suggestionTimer = setTimeout(() => fetchSuggestions(value), 180);
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
                    const cardId = button.dataset.cardId || "";
                    const adjustToggle = document.getElementById("stock-adjust-mode");
                    const reason = adjustToggle && adjustToggle.checked ? "adjust" : "sale";

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
                            button.dataset.cardId = result.cardId || "";

                            const decreaseButton =
                                button.parentElement.querySelector(".stock-button.decrease");

                            if (decreaseButton) {
                                decreaseButton.dataset.row = newRowIndex;
                                decreaseButton.dataset.cardId = result.cardId || "";
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
                                    cardId,
                                    change,
                                    reason
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
                relatedButton.disabled = normalizedQuantity <= 0;
                relatedButton.title = normalizedQuantity <= 0
                        ? "No hay unidades para vender"
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

    // ---- Pedido en preparación (reservas) ----
    // Se guarda en el navegador hasta confirmarlo en /reservas. No es un dato importante:
    // si se pierde, se vuelve a armar desde la búsqueda.
    const ORDER_KEY = "tcg-order-draft";
    const CART_OPEN_KEY = "tcg-cart-open";
    const moneyFormat = new Intl.NumberFormat("es-AR", {maximumFractionDigits: 0});

    function money(value) {
        return "$ " + moneyFormat.format(Math.round(Number(value) || 0));
    }

    function readOrder() {
        try {
            const parsed = JSON.parse(localStorage.getItem(ORDER_KEY) || "[]");
            return Array.isArray(parsed) ? parsed : [];
        } catch (e) {
            return [];
        }
    }

    function writeOrder(items) {
        try {
            localStorage.setItem(ORDER_KEY, JSON.stringify(items));
        } catch (e) {
            // Sin almacenamiento local el pedido solo vive en esta pantalla.
        }
        renderOrder(items);
    }

    function renderOrder(items) {
        renderCart(items);
        renderOrderDraft(items);
    }

    function orderPayload(items) {
        return JSON.stringify(items.map(item => ({cardId: item.cardId, quantity: item.quantity})));
    }

    function changeItem(index, delta) {
        const items = readOrder();
        const item = items[index];
        if (!item) {
            return;
        }
        const next = item.quantity + delta;
        if (delta > 0 && item.stock && next > item.stock) {
            showToast("No hay más unidades de " + item.name + " en stock", "error");
            return;
        }
        if (next <= 0) {
            items.splice(index, 1);
        } else {
            item.quantity = next;
        }
        writeOrder(items);
    }

    function cartRow(item, index) {
        const row = document.createElement("li");
        row.className = "cart-item";

        const info = document.createElement("div");
        info.className = "cart-item-info";
        const name = document.createElement("strong");
        name.textContent = item.name || "Carta";
        name.title = item.name || "";
        const set = document.createElement("span");
        set.textContent = item.set || "";
        set.title = item.set || "";
        const meta = document.createElement("span");
        const price = Number(item.price) || 0;
        const left = item.stock ? Math.max(item.stock - item.quantity, 0) : null;
        meta.textContent = (price > 0 ? money(price) + " c/u" : "sin precio")
            + (left === null ? "" : " · quedan " + left);
        if (price <= 0) {
            meta.classList.add("warn");
        }
        info.append(name);
        if (item.set) {
            info.append(set);
        }
        info.append(meta);

        const qty = document.createElement("div");
        qty.className = "cart-qty";
        const minus = document.createElement("button");
        minus.type = "button";
        minus.textContent = "−";
        minus.setAttribute("aria-label", "Quitar una unidad de " + item.name);
        minus.addEventListener("click", () => changeItem(index, -1));
        const count = document.createElement("span");
        count.textContent = item.quantity;
        const plus = document.createElement("button");
        plus.type = "button";
        plus.textContent = "+";
        plus.setAttribute("aria-label", "Sumar una unidad de " + item.name);
        plus.addEventListener("click", () => changeItem(index, 1));
        qty.append(minus, count, plus);

        const subtotal = document.createElement("span");
        subtotal.className = "cart-subtotal";
        subtotal.textContent = price > 0 ? money(price * item.quantity) : "-";

        row.append(info, qty, subtotal);
        return row;
    }

    function renderCart(items) {
        const panel = document.getElementById("cart-panel");
        if (!panel) {
            return;
        }
        const list = document.getElementById("cart-list");
        const units = items.reduce((sum, item) => sum + (Number(item.quantity) || 0), 0);
        const total = items.reduce((sum, item) => sum + (Number(item.price) || 0) * (Number(item.quantity) || 0), 0);
        const missingPrice = items.some(item => !(Number(item.price) > 0));

        panel.hidden = items.length === 0;
        document.body.classList.toggle("has-cart", items.length > 0);
        list.innerHTML = "";
        items.forEach((item, index) => list.appendChild(cartRow(item, index)));
        document.getElementById("cart-summary").textContent =
            units + (units === 1 ? " carta · " : " cartas · ") + money(total);
        document.getElementById("cart-total").textContent = money(total);
        document.getElementById("cart-items-input").value = orderPayload(items);
        document.getElementById("cart-price-warning").hidden = !missingPrice;
    }

    function renderOrderDraft(items) {
        const list = document.getElementById("order-draft-list");
        const input = document.getElementById("order-items-input");
        const form = document.getElementById("order-form");
        const empty = document.getElementById("order-draft-empty");
        if (!list || !input || !form) {
            return;
        }

        list.innerHTML = "";
        items.forEach((item, index) => list.appendChild(cartRow(item, index)));
        const total = items.reduce((sum, item) => sum + (Number(item.price) || 0) * (Number(item.quantity) || 0), 0);
        const totalLabel = document.getElementById("order-draft-total");
        if (totalLabel) {
            totalLabel.textContent = money(total);
            totalLabel.parentElement.hidden = items.length === 0;
        }

        input.value = orderPayload(items);
        const sellInput = document.getElementById("order-sell-items");
        if (sellInput) {
            sellInput.value = orderPayload(items);
        }
        form.hidden = items.length === 0;
        if (empty) {
            empty.hidden = items.length > 0;
        }
    }

    const cartToggle = document.getElementById("cart-toggle");
    if (cartToggle) {
        const panel = document.getElementById("cart-panel");
        const setOpen = open => {
            panel.classList.toggle("collapsed", !open);
            cartToggle.setAttribute("aria-expanded", String(open));
            try {
                localStorage.setItem(CART_OPEN_KEY, open ? "1" : "0");
            } catch (e) {
                // Preferencia opcional.
            }
        };
        let startOpen = !window.matchMedia("(max-width: 760px)").matches;
        try {
            const saved = localStorage.getItem(CART_OPEN_KEY);
            if (saved !== null && !window.matchMedia("(max-width: 760px)").matches) {
                startOpen = saved === "1";
            }
        } catch (e) {
            // Sin almacenamiento, queda el valor por defecto.
        }
        setOpen(startOpen);
        cartToggle.addEventListener("click", () => setOpen(panel.classList.contains("collapsed")));
    }

    const cartClear = document.getElementById("cart-clear");
    if (cartClear) {
        cartClear.addEventListener("click", () => writeOrder([]));
    }

    const cartSellForm = document.getElementById("cart-sell-form");
    if (cartSellForm) {
        cartSellForm.addEventListener("submit", event => {
            const items = readOrder();
            if (items.some(item => !(Number(item.price) > 0))) {
                event.preventDefault();
                showToast("Hay cartas sin precio. Sincronizá precios antes de cobrar.", "error");
            }
        });
    }

    document.querySelectorAll(".reserve-button").forEach(button => {
        button.addEventListener("click", () => {
            const cardId = button.dataset.cardId;
            if (!cardId) {
                showToast("Esta carta todavía no tiene ID. Sincronizá el inventario y volvé a buscar.", "error");
                return;
            }

            const items = readOrder();
            const existing = items.find(item => item.cardId === cardId);
            const stock = parseInt(button.dataset.stock || "0", 10) || 0;
            const current = existing ? existing.quantity : 0;

            if (current + 1 > stock) {
                showToast("No hay más unidades disponibles para sumar al carrito", "error");
                return;
            }

            const price = Number(button.dataset.price) || 0;
            if (existing) {
                existing.quantity += 1;
                existing.stock = stock;
                existing.price = price;
            } else {
                items.push({cardId, quantity: 1, name: button.dataset.name || "", set: button.dataset.set || "", price, stock});
            }

            writeOrder(items);
            const panel = document.getElementById("cart-panel");
            if (panel && panel.classList.contains("collapsed")) {
                showToast("Agregada al carrito", "success");
            }
        });
    });

    if (document.body.dataset.clearOrder === "true") {
        writeOrder([]);
    } else {
        renderOrder(readOrder());
    }

    const closeForm = document.getElementById("close-form");
    if (closeForm) {
        const expected = Number(document.getElementById("close-expected").dataset.expected) || 0;
        const parseAmount = value => Number(String(value || "").replace(/\$/g, "").replace(/\./g, "").replace(",", ".").trim()) || 0;
        const update = () => {
            const counted = ["efectivo", "transferencias", "tarjeta"]
                .reduce((sum, name) => sum + parseAmount(closeForm.elements[name].value), 0);
            const difference = Math.round(counted - expected);
            document.getElementById("close-counted").textContent = money(counted);
            const label = document.getElementById("close-difference");
            label.textContent = difference === 0 ? "$ 0" : (difference > 0 ? "+" : "-") + money(Math.abs(difference));
            label.className = difference === 0 ? "ok" : (difference > 0 ? "over" : "short");
        };
        closeForm.addEventListener("input", update);
        update();
    }

    document.querySelectorAll("[data-print]").forEach(button => {
        button.addEventListener("click", () => window.print());
    });

    // --- Compras a clientes ---
    const BUY_KEY = "tcg-buy-draft";
    const BUY_PERCENT_KEY = "tcg-buy-percent";

    function readBuy() {
        try {
            const parsed = JSON.parse(localStorage.getItem(BUY_KEY) || "[]");
            return Array.isArray(parsed) ? parsed : [];
        } catch (e) {
            return [];
        }
    }

    function writeBuy(items) {
        try {
            localStorage.setItem(BUY_KEY, JSON.stringify(items));
        } catch (e) {
            // Sin almacenamiento local la compra solo vive en esta pantalla.
        }
        renderBuyBadge(items);
        renderBuy(items);
    }

    function renderBuyBadge(items) {
        const badge = document.getElementById("buy-badge");
        if (!badge) {
            return;
        }
        const units = items.reduce((sum, item) => sum + (Number(item.quantity) || 0), 0);
        badge.hidden = units <= 0;
        badge.textContent = `Compra (${units}) →`;
    }

    function buyPercent() {
        const form = document.getElementById("buy-form");
        const payment = form ? form.querySelector("input[name=payment]:checked") : null;
        const input = document.getElementById(payment && payment.value === "credit" ? "buy-credit-percent" : "buy-cash-percent");
        return input ? Math.min(Math.max(Number(input.value) || 0, 0), 100) : 50;
    }

    function renderBuy(items) {
        const list = document.getElementById("buy-list");
        const form = document.getElementById("buy-form");
        if (!list || !form) {
            return;
        }
        const empty = document.getElementById("buy-empty");
        form.hidden = items.length === 0;
        if (empty) {
            empty.hidden = items.length > 0;
        }

        const percent = buyPercent();
        list.innerHTML = "";
        let total = 0;
        items.forEach((item, index) => {
            const offered = item.manual ? Number(item.unitPaid) || 0 : Math.round((Number(item.price) || 0) * percent / 100);
            item.unitPaid = offered;
            total += offered * item.quantity;

            const row = document.createElement("tr");
            const name = document.createElement("td");
            name.innerHTML = "";
            const strong = document.createElement("strong");
            strong.textContent = item.name;
            const meta = document.createElement("small");
            meta.textContent = [item.set, item.printing].filter(Boolean).join(" · ");
            name.append(strong, document.createElement("br"), meta);

            const price = document.createElement("td");
            price.className = "nowrap";
            price.textContent = Number(item.price) > 0 ? money(item.price) : "sin precio";

            const qty = document.createElement("td");
            const qtyBox = document.createElement("div");
            qtyBox.className = "cart-qty";
            const minus = document.createElement("button");
            minus.type = "button";
            minus.textContent = "−";
            minus.addEventListener("click", () => {
                const next = readBuy();
                next[index].quantity -= 1;
                if (next[index].quantity <= 0) {
                    next.splice(index, 1);
                }
                writeBuy(next);
            });
            const count = document.createElement("span");
            count.textContent = item.quantity;
            const plus = document.createElement("button");
            plus.type = "button";
            plus.textContent = "+";
            plus.addEventListener("click", () => {
                const next = readBuy();
                next[index].quantity += 1;
                writeBuy(next);
            });
            qtyBox.append(minus, count, plus);
            qty.appendChild(qtyBox);

            const paid = document.createElement("td");
            const paidInput = document.createElement("input");
            paidInput.type = "text";
            paidInput.inputMode = "numeric";
            paidInput.className = "buy-paid-input";
            paidInput.value = offered;
            paidInput.setAttribute("aria-label", "Pagás por " + item.name);
            paidInput.addEventListener("change", () => {
                const next = readBuy();
                next[index].unitPaid = Number(String(paidInput.value).replace(/\./g, "").replace(/[^0-9]/g, "")) || 0;
                next[index].manual = true;
                writeBuy(next);
            });
            paid.appendChild(paidInput);

            const subtotal = document.createElement("td");
            subtotal.className = "nowrap";
            subtotal.textContent = money(offered * item.quantity);

            const remove = document.createElement("td");
            const removeButton = document.createElement("button");
            removeButton.type = "button";
            removeButton.className = "text-action";
            removeButton.textContent = "Quitar";
            removeButton.addEventListener("click", () => {
                const next = readBuy();
                next.splice(index, 1);
                writeBuy(next);
            });
            remove.appendChild(removeButton);

            row.append(name, price, qty, paid, subtotal, remove);
            list.appendChild(row);
        });

        document.getElementById("buy-total").textContent = money(total);
        document.getElementById("buy-lines-input").value = JSON.stringify(items.map(item => ({
            sku: item.sku, cardId: item.cardId, quantity: item.quantity, unitPaid: item.unitPaid
        })));
    }

    document.querySelectorAll(".buy-button").forEach(button => {
        button.addEventListener("click", () => {
            const items = readBuy();
            const sku = button.dataset.sku;
            const existing = items.find(item => item.sku === sku);
            if (existing) {
                existing.quantity += 1;
            } else {
                items.push({
                    sku,
                    cardId: button.dataset.cardId || "",
                    name: button.dataset.name || "",
                    set: button.dataset.set || "",
                    printing: button.dataset.printing || "",
                    price: Number(button.dataset.price) || 0,
                    quantity: 1
                });
            }
            writeBuy(items);
            showToast("Sumada a la compra", "success");
        });
    });

    const buyForm = document.getElementById("buy-form");
    if (buyForm) {
        try {
            const saved = JSON.parse(localStorage.getItem(BUY_PERCENT_KEY) || "null");
            if (saved) {
                document.getElementById("buy-cash-percent").value = saved.cash;
                document.getElementById("buy-credit-percent").value = saved.credit;
            }
        } catch (e) {
            // Porcentajes por defecto.
        }
        buyForm.addEventListener("change", event => {
            if (event.target.name === "payment" || event.target.id === "buy-cash-percent" || event.target.id === "buy-credit-percent") {
                try {
                    localStorage.setItem(BUY_PERCENT_KEY, JSON.stringify({
                        cash: document.getElementById("buy-cash-percent").value,
                        credit: document.getElementById("buy-credit-percent").value
                    }));
                } catch (e) {
                    // Preferencia opcional.
                }
                const items = readBuy().map(item => ({...item, manual: false}));
                writeBuy(items);
            }
        });
        buyForm.addEventListener("submit", event => {
            const payment = buyForm.querySelector("input[name=payment]:checked");
            if (payment && payment.value === "credit" && !buyForm.elements.customer.value.trim()) {
                event.preventDefault();
                showToast("Para pagar con crédito poné el nombre del cliente", "error");
                buyForm.elements.customer.focus();
            }
        });
        document.getElementById("buy-clear").addEventListener("click", () => writeBuy([]));
    }

    if (document.body.dataset.clearBuy === "true") {
        writeBuy([]);
    } else {
        renderBuyBadge(readBuy());
        renderBuy(readBuy());
    }

    // --- Crédito al cobrar desde el carrito ---
    const creditInput = document.getElementById("cart-credit-customer");
    if (creditInput) {
        let loaded = false;
        creditInput.addEventListener("focus", () => {
            if (loaded) {
                return;
            }
            loaded = true;
            fetch(document.getElementById("cart-sell-form").action.replace(/ventas$/, "api/credito"))
                .then(response => response.ok ? response.json() : [])
                .then(accounts => {
                    const list = document.getElementById("cart-credit-list");
                    accounts.forEach(account => {
                        const option = document.createElement("option");
                        option.value = account.customer;
                        option.label = account.customer + " · " + money(account.balance);
                        list.appendChild(option);
                    });
                })
                .catch(() => {
                    loaded = false;
                });
        });
    }
});


// Vista grande de la carta al pasar el mouse por la miniatura de los resultados.
(function () {
    const thumbs = document.querySelectorAll(".card-thumb img");
    if (!thumbs.length || !window.matchMedia("(hover: hover)").matches) {
        return;
    }
    const preview = document.createElement("img");
    preview.className = "card-preview";
    preview.alt = "";
    preview.referrerPolicy = "no-referrer";
    document.body.appendChild(preview);

    function place(event) {
        const width = 220;
        const height = 306;
        let left = event.clientX + 24;
        let top = event.clientY - height / 2;
        if (left + width > window.innerWidth - 12) {
            left = event.clientX - width - 24;
        }
        top = Math.max(12, Math.min(top, window.innerHeight - height - 12));
        preview.style.left = left + "px";
        preview.style.top = top + "px";
    }

    thumbs.forEach(function (img) {
        img.addEventListener("mouseenter", function (event) {
            preview.src = img.src.replace("version=small", "version=normal");
            place(event);
            preview.classList.add("visible");
        });
        img.addEventListener("mousemove", place);
        img.addEventListener("mouseleave", function () {
            preview.classList.remove("visible");
        });
    });
})();
