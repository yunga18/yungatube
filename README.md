# YungaTube 🎬

**Cliente Android independiente**, con interfaz oscura inspirada en la organización de YouTube, sin pedir inicio de sesión ni contraseña de Google. Código abierto bajo la licencia [GPL-3.0-or-later](LICENSE).

## YungaTube v0.3.2

### Interfaz y navegación
- Inicio con **miniaturas grandes**, canales, duración y categorías temáticas.
- Búsqueda de videos, pestaña Shorts (resultados cortos), canales seguidos y videos relacionados.
- Biblioteca local: **Favoritos**, **Ver después**, **Historial** y **Descargas**.
- Menú de cada video para abrirlo, guardarlo o añadirlo a Ver después.

### Corrección v0.3.2
Se corrigió el cuadro del temporizador: Android daba prioridad a un mensaje informativo en lugar de mostrar la lista de tiempos. Ahora las opciones son seleccionables y el tiempo restante se muestra en el título.

### Temporizador de descanso
1. Reproduce un video y toca **☾ Temporizador** debajo de los controles.
2. Elige 5, 10, 15, 30, 45 o 60 minutos; también puedes introducir un tiempo de 1 a 240 minutos.
3. En la pantalla de reproducción se muestra el tiempo restante. Al llegar a cero, YungaTube **pausa**, pero no cierra, el reproductor.
4. Puedes sustituir el tiempo o tocar **Desactivar temporizador**. Si el servicio sigue activo, el temporizador también funciona con la pantalla apagada, en segundo plano o con la ventana flotante.

**Alcance:** el tiempo corre desde que lo activas (no depende de la duración del video ni se reinicia al cambiarlo). Es un temporizador de sesión: si Android cierra el proceso o reinicias el teléfono, se pierde la programación.

### Funciones parecidas a YouTube Premium, sin cuenta
| Función | Estado |
|---|---|
| Interfaz sin espacios publicitarios integrados | Sí; no garantiza eliminar toda publicidad que pueda incluir un flujo externo |
| Reproducción en segundo plano y pantalla bloqueada | Implementada con Android Media3 / MediaSessionService |
| Controles en notificación del sistema | MediaSessionService; requieren compatibilidad/permisos del dispositivo |
| Pantalla flotante (Picture-in-Picture) | Android 8+; también configurable al salir de la app |
| Temporizador para pausar automáticamente | Sí, 5–60 min o personalizado hasta 240 min, en la sesión de reproducción |\n| Cambiar velocidad de reproducción | 0.5× a 2× |
| Calidad de video | Selectable para las fuentes disponibles. Videos HD pueden necesitar flujos separados de audio y video |
| Reproducción de audio sin imagen | Disponible cuando el extractor ofrece un enlace de audio |
| Descargas MP4 compatibles para ver sin conexión | Sí, mediante DownloadManager; solo fuentes progresivas con audio y video |
| Historial, seguir canales, favoritos, Ver después | Guardados localmente en el teléfono |
| YouTube Music Premium, bitrate exclusivo, sincronización Google, contenido DRM y descargas protegidas | **No incluidos** |

### Importante
No es una APK de YouTube modificada ni un producto afiliado a YouTube. Es una aplicación independiente que utiliza [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) para consultar información pública de YouTube. Los servidores de YouTube y de Invidious pueden rechazar solicitudes o cambiar los formatos. **Las funciones pueden no operar con todos los videos**; una compilación exitosa solo confirma que la APK se generó, no que todas las funciones funcionan en un dispositivo real. Respeta los derechos de los creadores al guardar contenido.

Las descargas se guardan en el directorio de archivos privado de la aplicación, por lo que se eliminan al desinstalarla. No hay sincronización en la nube, ni uso de contraseñas de Google.

## Descarga e instalación gratis

1. Entra en [GitHub Actions de YungaTube](https://github.com/yunga18/yungatube/actions/workflows/android.yml).
2. Abre el **workflow verde más reciente**.
3. Desplázate hasta **Artifacts**, descarga `YungaTube-debug-APK` y extrae el ZIP.
4. Instala `app-debug.apk` en Android. No requiere root.

> **Firmas de pruebas:** GitHub Actions firma cada APK con una clave de depuración que puede cambiar entre ejecuciones. Si aparece un conflicto de firma, es posible que necesites desinstalar la versión anterior; **desinstalar borra favoritos, historial, descargas y listas locales**. El proyecto todavía necesita un proceso de firma estable para actualizaciones sin pérdida de datos.

## Desarrollar

Java 17, Android SDK 35, AGP 8.9.2, Gradle 8.11.1, Media3 1.9.4 y NewPipeExtractor v0.26.5.

La ejecución `Build Android APK` de GitHub Actions compila con `gradle :app:assembleDebug` y publica la APK como artefacto. También puedes abrir el proyecto en Android Studio.

## Créditos y licencia

Este repositorio contiene código propio de YungaTube y depende de NewPipeExtractor, licenciado bajo GPL-3.0-or-later. El código propio se publica bajo la misma familia de licencias. Consulta [LICENSE](LICENSE) y el proyecto original [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor).
