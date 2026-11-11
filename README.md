# Zero

**Asistente de voz personal que funciona 100 % en local**, escrito en Java.
Le hablas, te entiende, hace cosas en tu ordenador y te responde con una voz realista.
Nada sale de tu PC: ni tu voz, ni tus datos.

> 🤖 **Proyecto desarrollado con IA.** Todo el código de Zero lo ha escrito
> [Claude](https://claude.ai), de Anthropic. Yo soy estudiante de 1.º de DAM: he dirigido el
> proyecto, decidido qué hace y cómo se ve, lo he probado y depurado en mi PC, y lo uso para
> aprender Java leyéndolo y modificándolo. Ver [Sobre este proyecto](#sobre-este-proyecto).

## Qué hace

- 🎙️ Se activa diciendo **"Zero, ..."** y mantiene la conversación sin repetir el nombre.
- ⏰ **Recordatorios** que avisan a su hora y se repiten cada vez que enciendes el PC.
- 📦 **Instala y desinstala programas** con winget, siempre pidiendo confirmación.
- 🌐 Abre programas y webs, y busca en Google.
- 🖥️ Informa del uso de procesador, memoria y tarjeta gráfica.
- ⏲️ Temporizadores y notas.
- 🗣️ Voz realista y clonable con Chatterbox, o voces ligeras con Piper.
- 🖤 Interfaz oscura con Swing y FlatLaf, y arranque automático con Windows.

## Cómo funciona

```
 Micrófono ─► Java ─► servicio Python (Whisper: voz → texto)
                │
                ├─► Ollama (Qwen3: piensa y decide qué herramienta usar)
                │      └─► Herramientas Java: recordatorios (SQLite), winget, web...
                │
                └─► servicio Python (Chatterbox / Piper: texto → voz) ─► Altavoces
```

| Parte | Tecnología |
|---|---|
| Aplicación, interfaz y lógica | Java 21+, Swing, [FlatLaf](https://www.formdev.com/flatlaf/), Maven |
| Recordatorios | SQLite con JDBC |
| Cerebro | [Ollama](https://ollama.com) + Qwen3 4B |
| Oído | [faster-whisper](https://github.com/SYSTRAN/faster-whisper) (Whisper large-v3-turbo) |
| Voz | [Chatterbox](https://github.com/resemble-ai/chatterbox) y [Piper](https://github.com/rhasspy/piper) |

## Requisitos

- Windows 10/11, tarjeta NVIDIA con 8 GB de VRAM o más, 16 GB de RAM o más.
- JDK 21 o superior y Eclipse (o cualquier IDE con Maven).
- Python 3.12 (servicio principal) y Python 3.11 (voz realista).
- Ollama.

## Instalación

1. `servicio-ia\INSTALAR_SERVICIO.bat`: oído y voces Piper.
2. `servicio-ia\INSTALAR_VOZ_REALISTA.bat` (opcional): voz Chatterbox.
3. Importa el proyecto en Eclipse como *Existing Maven Project* y ejecuta `es.zero.Main`.
4. Para el arranque automático: `Run As > Maven build > package` y después `CONFIGURAR_ARRANQUE.bat`.

La guía completa, con el mapa del código y retos para aprender, está en [GUIA.md](GUIA.md).

## Sobre este proyecto

Zero es mi primer proyecto. Lo empecé sin saber nada de modelos de IA, Maven ni Git, y lo he
construido conversando con Claude: yo describía lo que quería, Claude escribía el código y me
explicaba cada parte, y yo lo probaba en mi PC y le contaba los errores hasta que funcionaba.

No lo presento como código escrito por mí. Lo presento como un proyecto que he **dirigido,
probado, entendido y que sigo ampliando**. A partir de la v1.1 intentaré hacer más cambios
por mi cuenta, y el historial de Git mostrará qué partes son mías.

## Inspiración

El nombre está inspirado en Zero, el alter ego de Lelouch en *Code Geass*.
Proyecto de fan sin ánimo de lucro, no afiliado con los propietarios de Code Geass.
El repositorio no incluye material de la serie.

## Versiones

- **v1.0**: primera versión completa.

## Licencia

[MIT](LICENSE). Los modelos que se descargan al instalar (Qwen3, Whisper, Piper, Chatterbox)
tienen sus propias licencias. Chatterbox está retirado por sus autores y ya no recibe
actualizaciones.
