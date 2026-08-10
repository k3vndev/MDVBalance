# MDVBalance 1.0.0

Plugin modular de MDVCRAFT para dos tareas iniciales:

1. Tutorial/onboarding de nuevos jugadores.
2. Límite económico de mobs generados por spawners vanilla.

Objetivo de plataforma: Paper/Purpur 1.21.6 + Java 21.

## Tutorial 1.0.0

Flujo fijo de cuatro objetivos, con textos/recordatorios configurables:

1. **Elegir raza**: se completa cuando LuckPerms confirma que el jugador hereda el grupo `aventurero` (fallback configurable por permiso).
2. **Reclamar Suministros**: se completa cuando PlayerKits2 ejecuta la señal de claim exitoso de MDVBalance.
3. **Viajar al Survival**: escucha el evento público de RTP exitoso de MDVRTP y acepta `COMMAND`, `SIGN` y `PORTAL`. También consulta el PDC persistente de MDVRTP como respaldo tras recargas/reconexiones.
4. **Establecer hogar**: comprueba que EssentialsX tenga al menos un home real; usa API por reflexión y userdata YAML como fallback.

El progreso se guarda en `plugins/MDVBalance/tutorial.db` (SQLite/WAL).

### BossBar y recordatorios

Cada objetivo muestra una BossBar del estilo:

`✦ Elige tu raza (1/4)`

Cada objetivo tiene en `config.yml`:

- `title`
- `start-message`
- `reminder.enabled`
- `reminder.interval-seconds`
- `reminder.message`
- `hide-player-chat`

No se crea un scheduler por jugador. Hay un único task liviano que comprueba únicamente jugadores online con tutorial activo.

### Filtro opcional de chat

Si el objetivo tiene `hide-player-chat: true`, el novato no recibe el chat escrito por jugadores normales. Los mensajes enviados por jugadores con `mdvbalance.tutorial.chat-bypass` sí se muestran. Los mensajes de plugins no se filtran porque el filtro trabaja sobre `AsyncChatEvent`.

### Comandos admin

- `/mdvbalance reload`
- `/mdvbalance tutorial start <jugador>`
- `/mdvbalance tutorial reset <jugador>`
- `/mdvbalance tutorial skip <jugador>`
- `/mdvbalance tutorial status <jugador>`
- `/mdvbalance spawners`

El comando interno de integración con kits es:

`mdvbalance tutorial signal <jugador> suministros`

Solo se acepta desde consola y no aparece en el tab-complete normal.

## PlayerKits2: Suministros

MDVBalance no considera suficiente abrir el GUI: el objetivo solo debe avanzar después de un claim exitoso.

En el kit de **Suministros**, agrega una acción de claim que ejecute desde consola:

```yaml
actions:
  claim:
    99:
      action: 'console_command: mdvbalance tutorial signal %player% suministros'
```

Usa un índice libre si `99` ya existe. Si tu archivo de PlayerKits2 ya tiene `actions.claim`, agrega solamente una nueva entrada, no dupliques la sección.

## MDVRTP

No hace falta modificar MDVRTP 1.1.0. MDVBalance intenta enlazarse dinámicamente a:

`xyz.mdvcraft.mdvrtp.api.event.MDVRandomTeleportSuccessEvent`

No hay dependencia Maven directa entre ambos plugins, por lo que MDVBalance puede compilar de forma independiente. Como respaldo lee:

- `mdvrtp:last_success_world`
- `mdvrtp:last_success_source`
- `mdvrtp:last_success_epoch`

El epoch debe ser posterior al momento en que empezó el objetivo 3, evitando que un RTP viejo complete el tutorial por accidente.

## Spawners vanilla

Configuración por defecto:

```yaml
spawners:
  enabled: true
  max-alive-per-spawner: 3
```

Cada entidad permitida por un `CreatureSpawner` recibe un PDC con las coordenadas/UUID de mundo de su spawner de origen. En RAM se mantiene:

`Spawner -> UUIDs de entidades que siguen existiendo`

El límite se consulta en O(1) cuando ocurre `SpawnerSpawnEvent`.

Importante: una descarga de chunk **no libera** el slot. Si los tres zombis se alejan a otro chunk o ese chunk se descarga, el spawner sigue considerándolos vivos. El slot se libera cuando Paper informa una eliminación real (muerte, despawn, plugin, etc.). Cuando una entidad marcada vuelve a cargarse, `EntitiesLoadEvent` reconstruye el tracker.

No hay escaneo global periódico de mobs ni de spawners.

## Compilar

```bash
mvn -B -DskipTests clean package
```

Salida:

`target/MDVBalance-1.0.0.jar`

El repositorio incluye GitHub Actions para Java 21 y una verificación adicional con Java 25.
