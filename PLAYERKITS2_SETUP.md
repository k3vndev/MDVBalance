# PlayerKits2 -> MDVBalance

El objetivo 2 debe confirmarse únicamente después de que PlayerKits2 entregue realmente el kit.

Dentro del kit de Suministros agrega una acción `console_command` en sus acciones de claim.

Ejemplo:

```yaml
actions:
  claim:
    99:
      action: 'console_command: mdvbalance tutorial signal %player% suministros'
```

Si ya existen acciones de claim:

```yaml
actions:
  claim:
    1:
      action: 'message: &aHas reclamado tus suministros.'
    99:
      action: 'console_command: mdvbalance tutorial signal %player% suministros'
```

La señal es deliberadamente console-only en MDVBalance. Un jugador no puede ejecutar el mismo comando para saltarse el objetivo.
