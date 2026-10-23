# Mantenimiento de Oído

Casi todo es automático:

- **Cada día** la tarea *Revisar actualizaciones* (en Actions) busca una versión nueva de NewPipe Extractor. Si la hay, compila con ella y, solo si compila bien, la guarda y publica un Release.
- **Cada cambio de código** que se sube al repositorio compila y publica un APK nuevo (*Construir APK*). Los cambios solo a `README.md`, `MANTENIMIENTO.md`, `logo.png` o `LICENSE` no generan APK.
- **La llave de firma no está en el código:** vive en los Secrets del repositorio (`OIDO_KEYSTORE` y `OIDO_KEY_PASSWORD`). Hay que guardar una copia fuera de GitHub; sin ella no se puede actualizar la app encima de la instalada.

Solo hay que intervenir si:

- GitHub avisa de una **compilación fallida**: en Actions, abrir la ejecución con ❌ → **View raw logs** y revisar las líneas `e:` o el bloque *What went wrong*.
- GitHub avisa de que **pausó las tareas** por 60 días sin cambios: Actions → la tarea → **Enable workflow**.
