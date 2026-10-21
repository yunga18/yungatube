# YungaTube 🎬

Cliente Android **independiente y sin inicio de sesión en Google**, desarrollado en Java.

## Funciones de esta primera versión

- Inicio con videos en tendencia, búsqueda y reproducción mediante instancias públicas de [Invidious](https://invidious.io/).
- Favoritos, historial y canales seguidos **guardados en el teléfono**, sin cuentas ni servidores propios.
- Pantalla de reproducción con controles nativos, cambio de orientación, sugerencias y enlaces para compartir.
- Configuración para elegir una instancia pública compatible o introducir una URL HTTPS propia.
- Diseño oscuro en español y acceso a las listas desde la barra inferior.
- Compilación reproducible en GitHub Actions, que genera un archivo APK como artefacto descargable.

## Límites y privacidad

Esta aplicación **no es YouTube oficial**, no usa OAuth de Google, no accede a suscripciones de tu cuenta de YouTube ni solicita contraseñas. Las instancias de Invidious son operadas por terceros y pueden registrar IP y solicitudes según sus políticas. La disponibilidad de búsquedas y reproducción depende de estas instancias y de los cambios de YouTube; **no se garantiza la ausencia de anuncios, funcionamiento permanente ni compatibilidad con todos los videos**.

El video se reproduce por las URLs proporcionadas por Invidious; si una instancia está bloqueada, cambia de servidor en ⚙️. La primera versión no incorpora descarga, Picture-in-Picture ni reproducción en segundo plano.

## Compilar e instalar APK (gratis)

1. Abre **Actions > Build Android APK** en este repositorio.
2. Selecciona **Run workflow** (o espera la ejecución automática después de un push).
3. Cuando termine en verde, abre la ejecución y descarga el artefacto `YungaTube-debug-APK`.
4. Extrae el ZIP, pasa `app-debug.apk` a tu Android e instálalo. Autoriza la instalación de esa fuente cuando Android lo solicite.

También puedes abrir el repositorio con Android Studio, seleccionar `app` y elegir **Build > Build APK(s)**. Requiere Android SDK 35, JDK 17 y Gradle compatible (el workflow configura este entorno).

> **Advertencia:** Una APK debug está firmada con una clave de depuración, por eso es para pruebas personales y no para publicar en Play Store. No descargues APK modificadas de sitios desconocidos.

## Base técnica

Android SDK 35 · Java 17 · Android Gradle Plugin 8.9.2 · Gradle 8.11.1 · Invidious REST API.

Licencia del código propio: MIT. Este proyecto no usa ni redistribuye código de las aplicaciones oficiales de Google/YouTube.

Fuente y documentación: [Invidious API](https://docs.invidious.io/api/) | [Instancias oficiales](https://docs.invidious.io/instances/).
