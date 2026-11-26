# Cómo instalar Oído en Android

Oído no viene de la Play Store, así que Android pide un **paso único** antes de instalarlo. Se hace **una sola vez por teléfono** y después Oído se instala y se actualiza normal.

> **¿Por qué?** Desde enero de 2027, Android pide un permiso especial para instalar apps de desarrolladores que no se registraron con Google. Oído no está registrado, a propósito: así no depende de Google. El paso de abajo es la forma oficial de Android para permitirlo.

**Hazlo con calma y con tiempo:** hay que esperar 24 horas en medio. Si ya usas Oído, conviene hacerlo **antes de enero**, para que las actualizaciones lleguen sin problemas.

---

## Paso único (una vez por teléfono)

### 1. Activa las "Opciones de desarrollador"

Entra a **Ajustes → Acerca del teléfono** y toca **7 veces seguidas** el **Número de compilación**. Te pedirá tu PIN y dirá "Ya eres desarrollador".

| Marca | Dónde está el número para tocar 7 veces |
|---|---|
| Samsung | Ajustes → Acerca del teléfono → Información de software → **Número de compilación** |
| Xiaomi / Redmi / POCO | Ajustes → Sobre el teléfono → **Versión de HyperOS** (o MIUI) |
| Motorola, Google Pixel y otros | Ajustes → Acerca del teléfono → **Número de compilación** |

### 2. Permite las apps de desarrolladores no verificados

Entra a **Opciones de desarrollador**:

- En la mayoría: **Ajustes → Sistema → Opciones de desarrollador**
- En Samsung: aparece al final de **Ajustes**

Busca **"Apps de desarrolladores no verificados"** (en inglés: *Apps from unverified developers*) y activa **"Permitir apps de desarrolladores no verificados"**. Android te preguntará si alguien te está presionando para hacerlo: responde con sinceridad (nadie te presiona, lo haces tú).

### 3. Reinicia el teléfono

### 4. Espera 24 horas

Es obligatorio, y es solo esta vez. El teléfono funciona completamente normal mientras tanto.

### 5. Vuelve a la misma opción y elige "Permitir indefinidamente"

Pasadas las 24 horas, regresa a **Opciones de desarrollador → Apps de desarrolladores no verificados**, confirma con tu huella o PIN y elige:

- ✅ **Permitir indefinidamente** ← elige esta
- ❌ *Permitir durante 7 días*: no la elijas, porque después tendrías que repetir todo para recibir actualizaciones de Oído.

### 6. (Opcional) Apaga las Opciones de desarrollador

Puedes apagarlas. El permiso se mantiene, y tus apps del banco funcionan normal.

---

## Instalar Oído

1. Descarga el archivo **.apk** de la última versión: **[Descargar Oído](https://github.com/KALAVER084/oido/releases/latest)** (en "Assets", el archivo que termina en `.apk`).
2. Ábrelo. Android mostrará un aviso de que el desarrollador no está verificado: toca **"Instalar de todas formas"**.
3. ¡Listo! Las próximas actualizaciones te las avisará Oído solo, y se instalan igual (con el mismo aviso).

---

## Preguntas frecuentes

**No encuentro "Apps de desarrolladores no verificados".**
Google está activando esta opción poco a poco. Si no aparece, es probable que tu teléfono todavía instale Oído sin este paso: prueba instalarlo directamente. Si Android lo bloquea y la opción no aparece, instala **"Android Developer Verifier"** desde la Play Store, espera un día y vuelve a buscarla.

**¿Es seguro?**
El permiso solo abre la puerta a las apps que tú decidas instalar. Instala apps únicamente de fuentes en las que confíes. El código completo de Oído es público en este repositorio.

**¿Tengo que repetirlo en cada actualización?**
No, si elegiste **"Permitir indefinidamente"**. Solo no desactives el permiso después: sin él, Android no deja actualizar Oído.

**¿Y si cambio de teléfono?**
El paso es por teléfono: en el nuevo hay que hacerlo otra vez.
