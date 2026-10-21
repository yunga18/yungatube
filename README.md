# YungaTube 🎬

Aplicación Android independiente, gratuita y sin inicio de sesión de Google.

## ¿Qué cambió en v0.2?

La primera versión dependía de cuatro instancias públicas de Invidious y podía mostrar *«No hay servidores disponibles»*. **YungaTube 0.2 cambia a conexión directa usando [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)**, sin depender de servidores públicos de Invidious para obtener búsquedas, tendencias, canales y videos.

- **Fuente predeterminada:** extracción local de información de YouTube mediante NewPipeExtractor v0.26.5.
- **Alternativa:** servidores Invidious en Configuración si el modo directo falla.
- **Sin cuenta:** no se solicita ni se almacena ninguna contraseña de Google.
- **Biblioteca local:** favoritos, historial y canales seguidos permanecen en el teléfono.
- **Reproductor:** reproduce formatos progresivos MP4 cuando YouTube los proporciona. Algunos videos pueden no funcionar, especialmente transmisiones en directo o cuando solo existen formatos DASH separados.
- **Privacidad:** la conexión directa envía solicitudes a servidores de YouTube, con los datos de red habituales; el modo Invidious utiliza servidores operados por terceros.
- **Limitaciones:** el acceso a YouTube es susceptible a cambios técnicos, restricciones regionales y bloqueos, de modo que una compilación exitosa no demuestra que los videos funcionen en todos los teléfonos.

### Descargar e instalar la APK

1. Abre [Actions](https://github.com/yunga18/yungatube/actions/workflows/android.yml).
2. Elige la **ejecución verde más reciente**.
3. En **Artifacts**, descarga `YungaTube-debug-APK`.
4. Descomprime el ZIP e instala `app-debug.apk` en Android. No hace falta Android Studio.

**Si tenías la versión anterior instalada:** esta APK debug puede tener una clave de firma diferente entre ejecuciones de GitHub Actions. Si Android indica *«No se instaló la aplicación»*, primero anota tus favoritos y canales seguidos y desinstala la versión anterior. Desinstalar eliminará los datos locales, ya que esta versión todavía no incluye exportación.

### Compilar en Android Studio

JDK 17, Android SDK 35, Android Gradle Plugin 8.9.2, Gradle 8.11.1. Usa `gradle :app:assembleDebug`. El workflow de GitHub Actions incluye la configuración gratuita.

## Licencias

El código propio de YungaTube se ofrece bajo **GPL-3.0-or-later**, debido a la integración de NewPipeExtractor. El código y la documentación de NewPipeExtractor están disponibles en [TeamNewPipe/NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), también bajo GPL-3.0-or-later. Consulta el texto de la licencia en [GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.html). YouTube es un servicio de Google y no está afiliado a este proyecto.

No se redistribuye código de la APK oficial de YouTube.
