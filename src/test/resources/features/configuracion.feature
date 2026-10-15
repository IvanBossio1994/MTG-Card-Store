Feature: Configuración
  Pantalla donde el comercio guarda su tienda, el Sheet del inventario, la regla de precio y los puntos.
  Corre contra el Sheet de QA; al terminar cada escenario el Sheet vuelve a como estaba.

  @HU-004 @CFG-F06 @feliz @critico
  Scenario: [Configuracion] Abre la pantalla de Configuración desde el menú
    Given abre la app
    When elige "Configuración" en el menú
    Then visualiza el módulo de "Configuración"

  @HU-004 @CFG-F06 @feliz @regresion
  Scenario: [Configuracion] Visualiza los cuatro bloques con los valores guardados
    Given abre el módulo de "Configuración"
    Then visualiza el bloque "Tienda"
    And visualiza el bloque "Inventario en Google Sheets"
    And visualiza el bloque "Regla de precio"
    And visualiza el bloque "Puntos de clientes"
    And el campo "Nombre de la tienda" muestra "Inventory Manager"
    And el campo "Pestaña del inventario" muestra "Inventario"
    And visualiza la cuenta de Gmail conectada

  @HU-004 @CFG-F01 @feliz @regresion
  Scenario: [Configuracion] Guarda el nombre de la tienda
    Given abre el módulo de "Configuración"
    When escribe "Codex Live Store" en "Nombre de la tienda"
    And hace clic en "Guardar tienda"
    Then visualiza el mensaje "Tienda guardada."

  @HU-004 @CFG-F02 @feliz @regresion
  Scenario: [Configuracion] Conecta un Sheet nuevo y deja la pestaña del inventario
    Given el Sheet de QA tiene solo la pestaña "Hoja 1"
    And abre el módulo de "Configuración"
    When pega el link del Sheet de QA
    And escribe "Inventario" en "Pestaña del inventario"
    And hace clic en "Guardar inventario"
    Then visualiza el mensaje "Se creó la pestaña «Inventario»."
    And valida que el Sheet de QA tenga la pestaña "Inventario"
    And valida que el Sheet de QA no tenga la pestaña "Hoja 1"

  @HU-007 @CFG-F02 @feliz @regresion
  Scenario: [Configuracion] Guarda el inventario trayendo las cartas de otra pestaña
    Given el Sheet de QA tiene solo la pestaña "stock viejo" con las cartas:
      | Carta    | Stock |
      | Sol Ring | 4     |
      | Island   | 10    |
    And abre el módulo de "Configuración"
    When pega el link del Sheet de QA
    And escribe "Stock 2026" en "Pestaña del inventario"
    Then la lista "Traer cartas de" tiene elegido "stock viejo · sugerida"
    When hace clic en "Guardar inventario"
    Then visualiza el mensaje "Se creó la pestaña «Stock 2026» con 2 cartas de «stock viejo»."
    And valida que el Sheet de QA tenga la pestaña "Stock 2026"
    And valida que el Sheet de QA tenga la pestaña "stock viejo"

  @HU-007 @CFG-F02 @feliz @regresion
  Scenario: [Configuracion] Guarda el inventario sin traer cartas
    Given el Sheet de QA tiene las pestañas vacías:
      | Hoja 1 |
      | Datos  |
    And abre el módulo de "Configuración"
    When pega el link del Sheet de QA
    And escribe "Stock 2026" en "Pestaña del inventario"
    And elige "No traer cartas" en "Traer cartas de"
    And hace clic en "Guardar inventario"
    Then visualiza el mensaje "Se creó la pestaña «Stock 2026»."
    And valida que el Sheet de QA tenga la pestaña "Stock 2026"

  @HU-007 @CFG-F03 @feliz @regresion
  Scenario: [Configuracion] Renombra una pestaña del Sheet
    Given el Sheet de QA tiene solo la pestaña "Hoja 1"
    And abre el módulo de "Configuración"
    When elige "Hoja 1" en "Pestaña a renombrar"
    And escribe "Stock 2026" en "Nombre nuevo"
    And hace clic en "Renombrar pestaña"
    Then visualiza el mensaje "La pestaña «Hoja 1» se renombró a «Stock 2026»."
    And el campo "Nombre nuevo" muestra ""
    And valida que el Sheet de QA tenga la pestaña "Stock 2026"
    And valida que el Sheet de QA no tenga la pestaña "Hoja 1"

  @HU-007 @CFG-F03 @feliz @regresion
  Scenario: [Configuracion] Renombra la pestaña del inventario y actualiza el campo
    Given el Sheet de QA tiene las pestañas vacías:
      | Inventario |
      | Datos      |
    And abre el módulo de "Configuración"
    When elige "Inventario · en uso" en "Pestaña a renombrar"
    And escribe "Stock 2026" en "Nombre nuevo"
    And hace clic en "Renombrar pestaña"
    Then visualiza el mensaje "También se actualizó la pestaña del inventario."
    And el campo "Pestaña del inventario" muestra "Stock 2026"
    And valida que el Sheet de QA tenga la pestaña "Stock 2026"

  @HU-007 @CFG-F03 @feliz @regresion
  Scenario: [Configuracion] Deshabilita las pestañas que usa la app
    Given el Sheet de QA tiene las pestañas vacías:
      | Hoja 1      |
      | Caja        |
      | Movimientos |
    When abre el módulo de "Configuración"
    Then la opción "Caja" de "Pestaña a renombrar" está deshabilitada
    And la opción "Movimientos" de "Pestaña a renombrar" está deshabilitada

  @HU-004 @CFG-F04 @feliz @regresion
  Scenario: [Configuracion] Guarda la regla de precio
    Given abre el módulo de "Configuración"
    When escribe "1400" en "Cotización (ARS por USD)"
    And hace clic en "Guardar precios"
    Then visualiza el mensaje "Precios guardados. Se recalculan."

  @HU-004 @CFG-F05 @feliz @regresion
  Scenario: [Configuracion] Guarda los pesos por punto
    Given abre el módulo de "Configuración"
    When escribe "500" en "Pesos por punto ($)"
    Then visualiza el ejemplo "Una compra de $25.000 suma 50 puntos."
    When hace clic en "Guardar puntos"
    Then visualiza el mensaje "Puntos guardados."

  @HU-004 @CFG-F06 @feliz @regresion
  Scenario: [Configuracion] Se queda editando al intentar salir con cambios sin guardar
    Given abre el módulo de "Configuración"
    When escribe "Tienda sin guardar" en "Nombre de la tienda"
    And elige "Inventario" en el menú
    Then visualiza el cartel "Hay cambios sin guardar"
    When hace clic en "Seguir editando"
    Then visualiza el módulo de "Configuración"
    And el campo "Nombre de la tienda" muestra "Tienda sin guardar"
