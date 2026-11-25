# Arquitectura de la Brújula

Carpeta = package (`android/app/src/main/java/com/zirakusa/brujula/`):

| Carpeta | Qué hay |
|---|---|
| `data/` | `Compass.kt`, reglas puras sin Android (con pruebas): `Accuracy` (precisión del sensor y si pide calibrar), `Heading` y `Fix` (modelos), `Angles` (vuelta al círculo y diferencia más corta), `AngleFilter` (suavizado que cruza el norte sin girar al revés), `Directions` (nombres de 8 o 16 rumbos) y `Dms` (grados, minutos y segundos). `Geo.kt`: `Geo` (distancia, rumbo, coordenadas escritas a mano, norte verdadero), `Sun` (dónde está el sol, fórmulas de la NOAA) y `Places` (lugares guardados en texto). `Calibration.kt`: progreso del 8 (26 direcciones del campo) y si el campo es el de la Tierra. |
| `sensors/` | `CompassSensor` (vector de rotación, o acelerómetro + magnetómetro si no hay; gira con la pantalla; la precisión sale del magnetómetro) y `LocationWatcher` (`LocationManager` de Android, sin Google Play services; GPS primero, que no necesita internet; satélites con `GnssStatus`; altitud). Solo escuchan mientras alguien los usa. |
| `ui/` | `CompassState` (único dueño de lo que se ve: rumbo, precisión, posición, página y Ajustes; abre y cierra «Calibrar» solo) y `BrujulaApp` (una página a la vez). |
| `ui/screens/` | `CompassScreen` (nombre del rumbo, esfera, grados, norte usado, «Ir a» y coordenadas), `LocationScreen` («Ubicación sin internet»: posición, satélites, norte verdadero y sol) y `PlacesCard` (Mis lugares, «Ir», escribir coordenadas), `Dial` (la esfera dibujada con Canvas), `CalibrationScreen` (el 8 verde animado), `SettingsScreen` (Ajustes), `McpScreen` (Modo MCP, copia de la de Notas) y `Chrome` (botones redondos, título, selector y filas). Solo pintan y avisan. |
| `ui/theme/` | `Theme` (`ThemeMode` Sistema/Claro/Oscuro, negro puro AMOLED, `Accent` y `CompassColors`, que todas las pantallas leen). |
| `mcp/` | Modo MCP, como Calculadora y Notas: `Tokens` (LLM/Hub/Redirect, siempre solo lectura, cifrados con `SecretBox`), `CompassTools` (`compass_heading` y `compass_location`; leen de la interfaz `CompassData`, así las pruebas usan una falsa), `McpHandler` (JSON-RPC, tokens, límites), `RelayLink` (relay de Stone), `McpMode` (estado e interruptores; `LiveCompass` da los sensores reales) y `HubLink` (enlace para Gateway Hub con firma). |
| `BrujulaApplication.kt` | Un solo sensor y una sola ubicación para la pantalla y el Modo MCP. |
| `MainActivity.kt` | Conecta los sensores con `CompassState` mientras la pantalla se ve, pide la ubicación solo si el usuario toca «Mostrar mi ubicación» y arranca el Modo MCP. |

Reglas:
- Texto visible en `res/values/strings.xml` (español) y `values-en`. Los nombres en español que salen por MCP están en
  `Directions`.
- Colores siempre de `CompassColors` (nunca fijos en una pantalla), para que el tema llegue a todo.
- Archivos de pantalla de unas 150 líneas como mucho; si crece, se divide.
- La pantalla muestra el norte verdadero cuando hay posición (declinación de `GeomagneticField`, el modelo magnético del
  propio Android, sin internet); si no, el magnético. `compass_heading` da los dos.
- Internet solo lo usa el Modo MCP (apagado de fábrica), hacia el relay.
