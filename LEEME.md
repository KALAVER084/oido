# Oído para Android

Busca en YouTube (videos, mixes y listas), escucha solo el audio y guarda tus propias listas.
Sigue sonando con la pantalla apagada o la app cerrada, con controles en la notificación,
la pantalla de bloqueo, audífonos Bluetooth y el carro.

## Cómo obtener el APK (gratis, sin instalar nada en tu PC)

GitHub compila la app en sus servidores y te deja el APK listo para bajar al teléfono.

1. Crea una cuenta en https://github.com (si no tienes).
2. Arriba a la derecha, **+ → New repository**. Nombre: `oido`. Marca **Private**. Pulsa **Create repository**.
3. En la página que aparece, pulsa **uploading an existing file**.
4. Descomprime el zip y arrastra **todo el contenido** de la carpeta `Oido-Android`
   (incluida la carpeta `.github`) a la página. Pulsa **Commit changes**.
5. Ve a la pestaña **Actions**. Verás "Construir APK" trabajando (tarda unos 5–10 minutos).
   Si GitHub pregunta si quieres activar los workflows, acepta.
6. Cuando salga la palomita verde, ve a la pestaña **Code** y, a la derecha, a **Releases**.
   Abre "Oído 1" y descarga `Oido-1.apk` **desde el teléfono**.
7. Ábrelo e instálalo. Android te pedirá permitir instalar apps de esa fuente: acéptalo.
8. La primera vez que abras Oído, permite las notificaciones (ahí aparecen los controles).

## Actualizaciones automáticas

Cada día, la tarea **Revisar actualizaciones** (en Actions) busca si NewPipe Extractor
(la pieza que habla con YouTube) tiene versión nueva. Si la hay, compila con ella y, si todo
sale bien, la guarda y publica un Release nuevo. Solo tienes que instalar el APK.

- Para enterarte: en tu repositorio, **Watch → Custom → Releases**. GitHub te avisará.
- Si la compilación falla, no se cambia nada y GitHub te manda un correo. Trae el log.
- Para revisar ya mismo: Actions → Revisar actualizaciones → **Run workflow**.

## Firma y aviso de versión nueva

La llave de firma **no está en el código**. Vive en los Secrets de este repositorio
(Settings → Secrets and variables → Actions):

- `OIDO_KEYSTORE`: la llave, en texto base64.
- `OIDO_KEY_PASSWORD`: su contraseña.

Guarda una copia de ambos fuera de GitHub. Sin esa llave no se puede actualizar la app
encima de la instalada (habría que desinstalar y se perderían las listas).

Cada copia de la app revisa los Releases de este repositorio al abrirse y, si hay versión
nueva, muestra un aviso con el botón **Descargar**.

GitHub pausa las tareas automáticas si el repositorio pasa 60 días sin cambios; te avisa por
correo y se reactivan en Actions con **Enable workflow**.

## Si algo falla al compilar

En **Actions**, abre la ejecución con la ❌, entra en "Compilar" y copia o toma captura
del error en rojo.

## Notas

- Anuncios: la app no carga páginas de YouTube ni su reproductor, solo pide el audio directo,
  así que no hay por dónde entrar anuncios. Además, la interfaz bloquea cualquier conexión
  que no sea de la propia app. Bloqueadores de todo el sistema (DNS privado, AdGuard, etc.)
  funcionan normal con ella.
- Tus listas se guardan dentro de la app. Desinstalarla las borra; instalar encima no.
- Usa NewPipe Extractor y código de NewPipe (licencia GPLv3), así que esta app también es GPLv3.
- Si el teléfono es Xiaomi, Huawei, Samsung u otra marca con ahorro de batería agresivo y la
  música se corta con la pantalla apagada: Ajustes → Apps → Oído → Batería → **Sin restricciones**.
