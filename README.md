# Brújula MCP

Brújula Android con el aspecto de la brújula del teléfono (fondo negro, esfera que gira, grados grandes y
coordenadas). La meta del proyecto es que una IA (Claude, ChatGPT…) pueda **leer el rumbo y la ubicación** del teléfono
por MCP, igual que Stone, Luma, Calculadora y Notas.

## Estado (0.1.0)

- Esfera dibujada a mano: marcas cada 2°, números cada 30°, N (con triángulo de color), E, S, O y la línea fija arriba.
- Rumbo en palabras (8 o 16 puntos: «Noroeste», «Nornoroeste») y grados en el centro.
- Latitud y longitud en grados, minutos y segundos, si permites la ubicación (opcional; sin servicios de Google).
- Sensor de vector de rotación (o acelerómetro + magnetómetro), suavizado y según el giro de la pantalla.
- «Calibrar la Brújula»: aparece sola cuando la precisión baja y se va sola cuando vuelve a ser alta.
- Ajustes: Temas (Sistema, Claro, Oscuro, negro puro AMOLED y color de acento), Rumbos, Modo MCP, Calibrar y versión.
- **Modo MCP** (solo lectura): `compass_heading` y `compass_location`. Mismo relay y Gateway Hub (`compass_`) que los
  demás.
- Internet solo para el Modo MCP (apagado de fábrica); sin anuncios ni copias en la nube.

APK: [`android/Brujula-0.1.0.apk`](android/Brujula-0.1.0.apk) (`com.zirakusa.brujula`, firmado como Stone y Gateway Hub).

## Compilar y probar

```bash
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

## Siguiente

Ver [`ROADMAP.md`](ROADMAP.md).
