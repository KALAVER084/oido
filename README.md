<p align="center">
  <img src="logo.png" width="120" alt="Logo de Oído">
</p>

<h1 align="center">Oído</h1>

<p align="center">
  Escucha YouTube sin anuncios, con la pantalla apagada, con letras y tus propias listas.
</p>

<p align="center">
  <a href="https://github.com/KALAVER084/oido/releases/latest"><b>⬇️ Descargar la última versión (APK)</b></a>
</p>

---

## Qué hace

Oído hace una sola cosa: **buscar en YouTube y reproducir solo el audio**.

- **Búsqueda completa:** audios, mixes y listas de reproducción, con filtros y orden por fecha (útil para podcasts).
- **Segundo plano real:** sigue sonando con la pantalla apagada o la app cerrada, con controles en la notificación, la pantalla de bloqueo, audífonos Bluetooth y el carro.
- **Sin anuncios:** nunca carga páginas ni el reproductor de YouTube; pide el audio directo.
- **Tus listas:** crea, ordena y reproduce listas propias. Guárdalas como copia (por ejemplo en Google Drive) o envíalas por WhatsApp o correo para pasarlas a otro teléfono.
- **Letras sincronizadas:** si la canción tiene letra, la muestra al ritmo de la música, en una franja o a pantalla completa. Si la versión no coincide (en vivo, videoclip con intro), la muestra fija para que nunca vaya desfasada.
- **En vivo:** transmisiones como Lofi Girl, en la calidad más baja para gastar pocos datos.
- **Compartir:** copia el enlace de cualquier audio, mix o lista.
- **Tema a tu gusto:** eliges un color y la app ajusta fondo, texto y acentos.
- **Se actualiza sola:** te avisa dentro de la app cuando hay versión nueva.

## Instalación

1. Desde el teléfono, abre **[la última versión](https://github.com/KALAVER084/oido/releases/latest)** y descarga el archivo `.apk`.
2. Ábrelo. Android te pedirá permitir instalar apps de esa fuente: acéptalo.
3. La primera vez que abras Oído, **permite las notificaciones** (ahí aparecen los controles).

> **Si la música se corta con la pantalla apagada** (pasa en Xiaomi, Huawei, Samsung y otras marcas con ahorro de batería agresivo):
> Ajustes → Apps → Oído → Batería → **Sin restricciones**.

Las versiones nuevas se instalan **encima** de la anterior y conservan tus listas.

## Privacidad

- No hay cuentas, registros ni estadísticas de uso.
- Tus listas y preferencias se guardan solo en tu teléfono.
- La app solo se conecta a YouTube (para buscar y reproducir), a [LRCLIB](https://lrclib.net) (para las letras) y a GitHub (para saber si hay versión nueva).
- Bloqueadores de anuncios de todo el sistema (DNS privado, AdGuard, etc.) funcionan normalmente con ella.

## Cómo funciona por dentro

| Pieza | Qué hace |
|---|---|
| [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) | Habla con YouTube: búsquedas, listas, mixes y la dirección del audio |
| [Media3 / ExoPlayer](https://developer.android.com/media/media3) | Reproduce en segundo plano, con notificación y pantalla de bloqueo |
| [LRCLIB](https://lrclib.net) | Base de datos libre de letras sincronizadas |
| WebView | Dibuja la interfaz (`app/src/main/assets/index.html`) |

## Mantenimiento

Casi todo es automático:

- **Cada día** la tarea *Revisar actualizaciones* (en Actions) busca una versión nueva de NewPipe Extractor. Si la hay, compila con ella y, solo si compila bien, la guarda y publica un Release.
- **Cada cambio** que se sube al repositorio compila y publica un APK nuevo (*Construir APK*).
- **La llave de firma no está en el código:** vive en los Secrets del repositorio (`OIDO_KEYSTORE` y `OIDO_KEY_PASSWORD`). Hay que guardar una copia fuera de GitHub; sin ella no se puede actualizar la app encima de la instalada.

Solo hay que intervenir si:

- GitHub avisa de una **compilación fallida**: en Actions, abrir la ejecución con ❌ → **View raw logs** y revisar las líneas `e:` o el bloque *What went wrong*.
- GitHub avisa de que **pausó las tareas** por 60 días sin cambios: Actions → la tarea → **Enable workflow**.

## Licencia

Oído usa NewPipe Extractor y código de [NewPipe](https://github.com/TeamNewPipe/NewPipe), que son software libre bajo licencia **GPLv3**, así que Oído también se distribuye bajo **GPLv3**.

Oído no está afiliado a YouTube ni a Google.
