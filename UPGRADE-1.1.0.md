# Actualizar MDVBalance 1.0.1 -> 1.1.0

MDVBalance 1.1.0 añade un módulo de lista de servidores que puede reemplazar ServerListPlus para el uso actual de MDVCRAFT.

## Qué añade

- MOTD normal configurable desde `plugins/MDVBalance/config.yml`.
- Cambio automático a un MOTD de mantenimiento cuando `/whitelist on` está activo.
- Vuelta automática al MOTD normal cuando `/whitelist off` está activo.
- Tooltip/hover configurable al pasar el mouse sobre la cantidad de jugadores.
- Placeholders internos `{online}`, `{players}`, `{max}`, `{minecraft_version}` y `{whitelist}`.
- Opción para dejar el MOTD normal en manos de `server.properties`, por ejemplo si quieres seguir editándolo desde el MOTD Editor de PebbleHost.
- No requiere ServerListPlus ni ProtocolLib.

## Actualización

1. Apaga el servidor.
2. Haz copia de seguridad de `plugins/MDVBalance/config.yml` y `tutorial.db`.
3. Reemplaza el JAR anterior por `MDVBalance-1.1.0.jar`.
4. **No borres `tutorial.db`.**
5. Añade manualmente a tu `config.yml` existente:

```yaml
modules:
  server-list: true
```

6. Copia la sección completa `server-list:` del `config.yml` incluido en 1.1.0 y personalízala.
7. Si MDVBalance controlará el MOTD directamente, elimina ServerListPlus para evitar que dos plugins intenten modificar el mismo ping.
8. Enciende el servidor y verifica:

```text
/mdvbalance serverlist
```

9. Prueba:

```text
/whitelist on
```

En un máximo aproximado de un segundo el servidor debe aparecer como mantenimiento en la lista. Después:

```text
/whitelist off
```

Debe volver al perfil normal.

## Usar el MOTD Editor de PebbleHost

Si prefieres que PebbleHost siga controlando el MOTD normal, usa:

```yaml
server-list:
  normal:
    motd:
      use-server-properties: true
```

En ese modo MDVBalance deja intacto el MOTD normal de `server.properties`, pero sigue reemplazándolo automáticamente mientras la whitelist esté activa.

El panel de PebbleHost no evalúa placeholders de MDVBalance. Los placeholders solo funcionan dentro de la configuración del módulo `server-list` de MDVBalance.
