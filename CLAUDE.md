# Brújula MCP: reglas para agentes

- Lee `ARQUITECTURA.md` y abre solo los archivos que necesites.
- Pruebas: `cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` (debe pasar todo).
  Nunca desactives ni saltes una prueba. Lint sin avisos nuevos.
- Cada versión: sube `versionCode` y `versionName`, copia el APK a `android/Brujula-<versión>.apk` (borra el anterior)
  y anótalo en `CHANGELOG.md`.
- Privacidad: el rumbo y la ubicación se leen solo en el teléfono; la ubicación solo con permiso del usuario (opcional) y
  sin servicios de Google. Nada sale salvo por el Modo MCP que el usuario active; internet es solo para eso.
- El Modo MCP es solo de lectura: ninguna herramienta cambia nada. Cada herramienta MCP nueva lleva su prueba en
  `McpTest`: lista de herramientas, llamada correcta, argumentos malos y «no encontrado».
- Prohibido: `eval`, motores de scripts o reflexión.
- Firma: la de depuración del entorno, igual que Stone, Luma, Calculadora, Notas, Filem y Gateway Hub.
