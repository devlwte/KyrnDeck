# KyrnDeck

Un launcher para Meta Quest que pone todas tus apps en una sola lista: las de la tienda, las de fuentes desconocidas, los juegos VR y las apps 2D de Android.

El menú de Meta separa lo que no viene de la tienda en "Fuentes desconocidas" y a veces esa sección ni aparece cuando el visor está sin internet. KyrnDeck lee directamente lo que hay instalado, así que todo sale junto y se abre desde el mismo sitio.

## Qué tiene

- Filtros por tienda, desconocidas, VR, 2D, favoritas y sistema, con contador en cada uno.
- Búsqueda por nombre o paquete.
- Orden por nombre, recientes, más usadas o instaladas hace poco.
- Fila "Continuar" con lo último que abriste y fila de favoritas.
- Ficha de cada app: versión, tamaño, fechas, quién la instaló y tiempo de uso. Desde ahí se abre, se marca como favorita, se van a sus ajustes o se desinstala.
- Hora, batería y espacio libre del visor en la barra de arriba.
- Tema claro, oscuro o automático.
- Se adapta al tamaño del panel: lo puedes estirar o encoger y la interfaz se reorganiza.
- Se actualiza sola desde las releases de este repositorio.

## Instalar

Hace falta el modo desarrollador del Quest activado (se activa desde la app Meta Horizon del móvil).

La forma más cómoda es descargar el `.zip` de la última [release](https://github.com/devlwte/kyrndeck/releases/latest), conectar el visor por USB y abrir `Instalar-KyrnDeck.bat`. Instala la app, activa los permisos y la abre.

También se puede instalar el `.apk` con SideQuest o con `adb install`. En ese caso conviene dar estos dos permisos desde el PC, porque el visor no los ofrece en sus ajustes:

```
adb shell appops set com.kyrnforge.kyrndeck GET_USAGE_STATS allow
adb shell appops set com.kyrnforge.kyrndeck REQUEST_INSTALL_PACKAGES allow
```

El primero sirve para que "Recientes" y "Más usadas" cuenten también lo que abres desde el menú de Meta. El segundo deja que KyrnDeck instale sus propias actualizaciones.

La app queda en Biblioteca, Fuentes desconocidas. Lo mejor es anclarla a la barra del visor.

## Compilar

Proyecto Android en `android/`, Kotlin y Jetpack Compose.

```
cd android
./gradlew assembleRelease
```

La firma de release se lee de un `keystore.properties` que no está en el repositorio. Sin él, se puede compilar en debug con `./gradlew assembleDebug`.

## Licencia

MIT
