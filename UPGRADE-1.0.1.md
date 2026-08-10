# MDVBalance 1.0.1 — actualización desde 1.0.0

No borres `config.yml` ni `tutorial.db`.

1. Apaga el servidor con `/stop`.
2. Reemplaza `MDVBalance-1.0.0.jar` por `MDVBalance-1.0.1.jar`.
3. Conserva `plugins/MDVBalance/config.yml`.
4. Añade, si quieres configurarlos explícitamente:

```yaml
tutorial:
  sounds:
    objective-complete:
      enabled: true
      sound: 'minecraft:block.note_block.pling'
      volume: 1.0
      pitch: 1.6

    tutorial-complete:
      enabled: true
      sound: 'minecraft:ui.toast.challenge_complete'
      volume: 1.0
      pitch: 1.0
```

Los objetivos 1-3 reproducen `objective-complete`. El objetivo 4 reproduce solamente `tutorial-complete` para que no suenen dos sonidos simultáneamente.
