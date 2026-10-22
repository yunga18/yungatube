# YungaTube 🎬

Aplicación Android independiente, gratuita y sin cuenta de Google. El código está publicado bajo GPL-3.0-or-later.

## Solución definitiva a «El paquete no es válido» (v0.3.3)

Las versiones iniciales eran APK de depuración generadas por GitHub Actions. Cada runner podía crear una **clave de firma distinta**. Android no permite actualizar una app con la misma ID de paquete y firma distinta; no basta con subir el número de versión.

### Lo que corregimos

- APK de prueba: ID `com.yunga.yungatube.prueba`, etiquetada **YungaTube Prueba**. Se genera siempre y se instala sin reemplazar ninguna de las antiguas.
- APK de uso diario: ID `com.yunga.yungatube.estable`, etiquetada **YungaTube**. Se firma con un **certificado permanente** configurado mediante un secreto privado en GitHub.
- La primera instalación estable aparece en paralelo a las versiones antiguas, sin borrarlas. Todas las APK **estables siguientes** usarán la misma clave, por lo que podrás actualizarlas normalmente.
- El repositorio público NO contiene el archivo JKS privado ni su contraseña.

### Un único paso privado para activar futuras actualizaciones

El propietario debe configurar **una sola vez** un secreto del repositorio:

1. Abre **Settings > Secrets and variables > Actions > New repository secret**.
2. En **Name**, escribe `YUNGATUBE_SIGNING_BUNDLE`.
3. En **Secret**, pega el valor del archivo privado `YungaTube_VALOR_SECRETO_GITHUB.txt` entregado directamente al propietario. No se debe subir dicho archivo ni la clave JKS al repositorio.
4. Pulsa **Add secret**.
5. Abre **Actions > Build Android APK > Run workflow**.
6. En **Artifacts**, descarga **YungaTube-firma-estable** (NO el que dice «PRUEBA»). Extrae `app-release.apk` del ZIP e instálalo.

Conserva una copia privada del JKS y de la contraseña. **Perderlos impide firmar actualizaciones para quienes ya tengan instalada la aplicación estable.**

### Si ya tenías una APK de prueba instalada

La firma antigua NO se puede recuperar de un APK. Por tanto, no puede instalarse una actualización encima de esas versiones sin tener la clave privada que firmó la instalación. Puedes **conservar tu app antigua** y instalar YungaTube estable en paralelo; tus favoritos e historial antiguos seguirán en la app antigua, pero todavía no se transferirán automáticamente a la nueva. ¡No desinstales la versión antigua si necesitas esos datos!

El archivo descargado de GitHub Actions es un ZIP; debes **extraer el APK** que tiene dentro antes de abrir el instalador Android. No trates de instalar el ZIP.

### Funciones
Inicio con miniaturas, búsqueda, Shorts, suscripciones locales, favoritos, Ver después, historial, descargas cuando el formato lo permite, reproducción en segundo plano, pantalla flotante, calidad y velocidad configurables.

**Temporizador para pausar:** al reproducir, entra en «☾ Temporizador», selecciona 5, 10, 15, 30, 45 o 60 minutos, o elige un tiempo personalizado de 1 a 240 minutos. Puedes ver el tiempo restante y cancelar la cuenta atrás.

### Limitaciones
No se garantiza que todos los videos de YouTube funcionen. NewPipeExtractor e Invidious pueden fallar tras cambios de YouTube. No incluye membresía YouTube Premium ni sincronización de cuentas de Google. El modo de desarrollo es para pruebas y **no garantiza actualizaciones**.

## Compilar localmente
Java 17, Gradle 8.11.1, Android SDK 35, Media3 y NewPipeExtractor. `gradle :app:assembleDebug` genera la APK de prueba. Para release necesita la variable de entorno `YUNGATUBE_SIGNING_BUNDLE` con el JSON del secreto privado.

## Licencias
YungaTube: GPL-3.0-or-later, por la dependencia de [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor). Consulta [LICENSE](LICENSE).
