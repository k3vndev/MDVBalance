# Instalación rápida — MDVBalance 1.0.0

1. Compila el proyecto con Maven o GitHub Actions.
2. Copia `MDVBalance-1.0.0.jar` a `plugins/`.
3. Mantén instalados LuckPerms, EssentialsX, MDVSocial, MDVRTP y PlayerKits2.
4. Inicia el servidor una vez para generar `plugins/MDVBalance/config.yml` y `tutorial.db`.
5. Configura la acción de claim del kit Suministros siguiendo `PLAYERKITS2_SETUP.md`.
6. Reinicia el servidor.

## Prueba recomendada con tu cuenta

```text
/mdvbalance tutorial reset TU_NOMBRE
/mdvbalance tutorial status TU_NOMBRE
```

Si tu cuenta ya pertenece al grupo Aventurero, el objetivo 1 se completará automáticamente. Para probarlo desde cero de verdad, usa una cuenta sin ese grupo o retíralo temporalmente.

## Prueba de spawner

Deja `max-alive-per-spawner: 3`, acércate a un spawner y confirma que nunca mantiene más de 3 entidades originadas por ese mismo bloque. Puedes ver el tamaño del tracker con:

```text
/mdvbalance spawners
```
