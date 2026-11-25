# Brújula 0.2.0

- **Ubicación sin internet**: toca las coordenadas. El GPS no necesita datos ni Wi‑Fi: se ve la posición en decimales y en
  grados, la precisión, la altitud, cuántos satélites se usan y hace cuánto se actualizó. «Copiar» y «Guardar este lugar».
- **Norte verdadero siempre** (de fábrica): con tu posición, el teléfono calcula la declinación magnética con su propio
  modelo (sin internet) y la brújula marca el norte de los mapas. Se puede volver al magnético.
- **El sol en la esfera**: un punto amarillo muestra dónde está el sol ahora, calculado con la hora y tu posición. Sirve
  para comprobar el norte aunque el sensor falle.
- **Mis lugares e «Ir»**: guarda dónde estás o escribe coordenadas (decimales o grados, minutos y segundos); con «Ir» una
  flecha verde en la esfera y «Ir a Coche · 741 m · 320°» te llevan de vuelta.
- **Calibración mejor**: una barra de progreso que se llena mientras dibujas el 8, la precisión del sensor y la fuerza del
  campo magnético (µT), con aviso si hay un imán, metal o el cargador cerca. Ya no se cierra sola con un imán cerca.
- **Iconos nuevos**: Óleo (el de fábrica), Metal, Clásica, Neón y Píxel. Se quitan los anteriores; si tenías uno elegido,
  pasa solo al nuevo que ocupa su lugar.

# Brújula 0.1.1

- **Conectar todas** (Gateway Hub 0.0.21): con un toque en el Hub se activa aquí «Redireccionar a Hub» y la app queda
  vinculada, sin entrar en ella. Solo lo puede pedir una app firmada por el mismo desarrollador.
- **Lucía** (la voz del Hub) usa las herramientas de esta app en el teléfono, **sin internet**, con las mismas reglas que
  por MCP. Solo mientras «Redireccionar a Hub» esté activado.
- **Icono de la app**: 5 iconos para elegir (Original, Claro, Dorado, Azul y Verde) en Ajustes › Icono de la app. Tras esta
  actualización quizá tengas que volver a poner el icono en tu pantalla de inicio (una sola vez).

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
