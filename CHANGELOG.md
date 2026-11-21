# Brújula 0.1.0

Primera versión.
- **Brújula** como la del teléfono: fondo negro, rumbo en palabras arriba («Noroeste»; 8 o 16 puntos en Ajustes), esfera
  que gira con marcas cada 2°, números cada 30°, N con triángulo, E, S, O, la línea fija arriba y los grados en el centro.
- **Latitud y longitud** abajo en grados, minutos y segundos («Latitud sur 3°36′23″»), solo si permites la ubicación
  (se pide con un botón, es opcional). Usa el `LocationManager` de Android, sin servicios de Google.
- Sensor de **vector de rotación** (o acelerómetro + magnetómetro si no hay), suavizado sin saltos al cruzar el norte y
  correcto con la pantalla girada. La pantalla no se apaga mientras la brújula está abierta.
- **Calibrar la Brújula**: un 8 verde animado con su flecha. Aparece solo cuando la precisión baja y se va sola cuando
  vuelve a ser alta; también desde Ajustes.
- **Ajustes**: Temas (Sistema, Claro, Oscuro; negro puro AMOLED; color de acento), Rumbos, Modo MCP, Calibrar,
  Política de privacidad y versión.
- **Modo MCP**, igual que Stone, Luma, Calculadora y Notas: Activar MCP, Crear token (LLM o Hub, caducidad), pausar,
  revocar y **Redireccionar a Hub**. Solo lectura: `compass_heading` (grados magnéticos y verdaderos, rumbo, precisión,
  calibrando) y `compass_location` (decimal y grados-minutos-segundos, precisión; errores `NO_PERMISSION` y `NO_FIX`).
  Tokens `bru_…`; en el hub aparece como `compass_`.
- 16 pruebas.
