# Smart Ring R99 Bridge & Health Connect para Samsung Galaxy S24

Aplicación nativa de Android desarrollada en **Kotlin moderno con Jetpack Compose y Android Health Connect API** para controlar y sincronizar métricas del **Smart Ring R99** (anillo inteligente con hardware tipo "REA") hacia **Samsung Health** y Google Fit de manera 100% local, sin nubes de terceros.

---

## 🚀 Características principales

1. **Extractor BLE y telemetría de hardware:**
   * **Escaneo automático:** Detección de dispositivos por prefijo de nombre `R99` (ej. `R99 89BC`).
   * **Servicio de Frecuencia Cardíaca (0x180D / 0x2A37):** Decodificación de flags estándar (UINT8 / UINT16) con ventana de deduplicación de 15 segundos para evitar registros redundantes segundo a segundo.
   * **Nordic UART Service (NUS):** 
     * Suscripción a canal TX (`6e400003-b5a3-f393-e0a9-e50e24dcca9e`).
     * Transmisión de comandos por canal RX (`6e400002-b5a3-f393-e0a9-e50e24dcca9e`).
   * **Parser de telemetría:**
     * Bloques consolidados: `STEPS: <int> CALO: <int>, DIST: <int>`.
     * Ticks de caminata instantánea: `STEPS: 1` con filtro debounce de 200 ms para eliminar ecos por milisegundos sin limitar zancadas rápidas.
     * Estados de actividad: `MEAS_EVT_ACT_STATE` (1,0 = En movimiento, 0,0 = Reposo).
     * Detección de uso y eventos de sueño: `sleep_wear_state OK` y `sleep evt id <id>`.
   * **Keepalive:** Trama de ping de sincronización binaria `[0xA5, 0x01, 0x00, 0xA6]` enviada a NUS RX cada 30 segundos.

2. **Ahorro de batería del anillo (Celda de 18 a 20 mAh):**
   * Configurado para ciclos de sincronización de **30 minutos** mediante `WorkManager`.
   * El worker se despierta, se conecta por BLE, extrae el consolidado de pasos, distancia, calorías y pulso durante ~45 segundos, inyecta los datos a Health Connect y desconecta el GATT para devolver el procesador del anillo a su estado de mínimo consumo energético.

3. **Integración nativa con Health Connect (Samsung Health):**
   * Compatible con Android 14 (API 34) y One UI 6 de Samsung.
   * Escribe directamente en el almacén unificado del sistema:
     * `StepsRecord`
     * `HeartRateRecord`
     * `DistanceRecord`
     * `TotalCaloriesBurnedRecord`
   * Actividad de justificación de privacidad (`PermissionsRationaleActivity`) registrada para que Samsung Health lo reconozca como fuente legítima de datos biométricos.

4. **Exportación local a CSV:**
   * Permite descargar el historial completo de la sesión directamente en la carpeta pública `Downloads/` del celular mediante Android `MediaStore` sin permisos intrusivos.

---

## 📱 ¿Cómo obtener e instalar el archivo APK en tu Galaxy S24?

Dado que tu computadora no tiene instalado Android Studio o Java, dispones de dos opciones muy sencillas:

### Opción A. Compilación automática en la nube vía GitHub Actions (Recomendada)
Este proyecto incluye el flujo de automatización listo en `.github/workflows/build-apk.yml`.

1. Inicializa un repositorio Git y súbelo a tu cuenta de GitHub:
   ```bash
   cd c:\Users\SDVP\Documents\GitHub\smartring-r99-bridge
   git init
   git add .
   git commit -m "feat: Smart Ring R99 Bridge & Health Connect inicial"
   git branch -M main
   git remote add origin https://github.com/TU_USUARIO/smartring-r99-bridge.git
   git push -u origin main
   ```
2. Abre tu repositorio en GitHub desde tu computadora o desde el navegador de tu Galaxy S24.
3. Ve a la pestaña **Actions**.
4. Haz clic en la ejecución llamada **"Compilar APK SmartRing R99 Bridge"**.
5. Al finalizar (tarda ~2 minutos), en la sección inferior **Artifacts**, descarga el archivo ZIP que contiene `app-debug.apk`.
6. Cópialo o ábrelo en tu Galaxy S24 para instalarlo.

---

### Opción B. Compilación local con Android Studio
Si prefieres compilar directamente en tu máquina:
1. Descarga e instala [Android Studio Jellyfish o superior](https://developer.android.com/studio).
2. Abre la carpeta `c:\Users\SDVP\Documents\GitHub\smartring-r99-bridge`.
3. Espera a que Gradle descargue las dependencias.
4. En el menú superior, ve a **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. El APK se generará en:
   `app/build/outputs/apk/debug/app-debug.apk`
6. Conecta tu Galaxy S24 por cable USB y copia el APK a la carpeta `Download` del celular, o envíatelo por WhatsApp / Google Drive.

---

## ⚙️ Configuración y vinculación con Samsung Health en el Galaxy S24

Una vez instalado el APK en el celular:

1. **Abrir la app "Smart Ring R99 Bridge":**
   * La app te solicitará permisos de **Dispositivos cercanos (Bluetooth)** y **Notificaciones**. Acéptalos para permitir el escaneo BLE.
2. **Autorizar Health Connect:**
   * En la pantalla principal aparecerá una tarjeta naranja: *"Vincular con Samsung Health"*.
   * Presiona **"Autorizar en Health Connect"** y activa los interruptores para permitir la escritura de Pasos, Frecuencia Cardíaca, Distancia y Calorías.
3. **Vincular en Samsung Health (One UI 6):**
   * Abre la app **Samsung Health** en tu Galaxy S24.
   * Toca los tres puntos verticales en la esquina superior derecha > **Ajustes**.
   * Selecciona **Health Connect**.
   * Verifica que en la lista de aplicaciones aparezca **Smart Ring R99 Bridge** con los permisos de escritura habilitados.
4. **Sincronización:**
   * Asegúrate de que el anillo no esté conectado a la app china SmartHealth (el anillo solo acepta 1 conexión simultánea).
   * Pulsa **"Sincronizar ahora con Health Connect"** o deja activado el interruptor de **Sincronización en 2º plano (30 min)**.
   * Tus pasos y pulso comenzarán a aparecer reflejados dentro del panel principal de Samsung Health.
