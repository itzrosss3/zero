# ZERO en Java · guía para tirarse a la piscina

Zero es un asistente de voz que funciona entero en tu PC. Esta versión está escrita en **Java**
para que puedas leerla, romperla y arreglarla mientras aprendes.

```
                         ┌──────────────── TU CÓDIGO JAVA ────────────────┐
  Micrófono ──────────►  │ Microfono ─► Nucleo ─► Cerebro ──HTTP──► Ollama │ (modelo de IA)
                         │                │          │                      │
                         │                │      Herramientas (recordatorios,│
                         │                │       programas, web...)  ─► SQLite
                         │                ▼                                  │
  Altavoces  ◄────────── │              Voz        Ventana (Swing)          │
                         └───────┬────────┬─────────────────────────────────┘
                                 │ HTTP   │
                         ┌───────▼────────▼───────┐
                         │ servicio-ia (Python)   │  Whisper: voz -> texto
                         │ no hace falta tocarlo  │  Piper:   texto -> voz
                         └────────────────────────┘
```

Java hace todo lo "tuyo": la ventana, escuchar el micro, decidir qué hacer, los recordatorios,
instalar programas... Solo delega en Python lo que necesita la tarjeta gráfica (entender tu voz y
generar la de Zero), igual que delega el cerebro en Ollama. Muchas aplicaciones reales están
montadas así: varias piezas que se hablan por HTTP.

---

## 1. Instalación

Ya tienes **Python 3.12** y **Ollama** de la versión anterior. Te falta:

1. **Un JDK 21 o superior** (el que uséis en clase vale) y **Eclipse IDE for Java Developers**.
   Comprueba que tu versión de Eclipse es compatible con tu versión de Java: si Eclipse es más viejo
   que el JDK, actualízalo.
2. Descomprime el zip en una carpeta fija, por ejemplo `C:\Proyectos\zero`.
3. Doble clic en **`servicio-ia\INSTALAR_SERVICIO.bat`**. Instala el oído y la voz.
   La primera vez tarda (descarga unos 3 GB de librerías de la gráfica).

> Si tenías la versión en Python de Zero arrancando sola, ya no hace falta: la configuración de
> arranque de esta versión (paso 5) la sustituye. Puedes borrar la carpeta antigua cuando quieras.

## 2. Abrir el proyecto en Eclipse

1. `File > Import... > Maven > Existing Maven Projects > Next`.
2. En *Root Directory* elige la carpeta `zero` (la que tiene el `pom.xml`) y pulsa **Finish**.
3. Abajo a la derecha verás una barra de progreso: Eclipse está **descargando las librerías**.
   Espera a que termine.

Si ves errores rojos del tipo *"JRE System Library"*: clic derecho en el proyecto >
`Maven > Update Project... > OK`.

## 3. Ejecutar

Abre `src/main/java/es/zero/Main.java` y pulsa **Run As > Java Application** (o el botón verde ▶).

- La primera vez tarda en arrancar (carga los modelos en la gráfica). Las siguientes, mucho menos.
- La **consola de Eclipse** muestra todo lo que pasa por dentro: es tu mejor amiga para depurar.
- Para instalar programas sin avisos de Windows, Zero necesita permisos de administrador. Desde Eclipse
  te saldrá el aviso de Windows en cada instalación; con el arranque automático (paso 5) no.

## 4. Maven en dos minutos

Maven es la herramienta que gestiona el proyecto. Lo importante:

- **`pom.xml`** es la ficha del proyecto: nombre, versión de Java y **librerías** (`<dependencies>`).
  Para usar una librería nueva, añades su bloque `<dependency>` y Eclipse la descarga sola.
  Las librerías se buscan en https://mvnrepository.com
- **Estructura obligatoria**: el código va en `src/main/java`. Si respetas eso, Maven sabe compilarlo.
- **`package`** es la orden que genera el programa final, `target/zero.jar`, con todo dentro.

Librerías que usa Zero:

| Librería | Para qué |
|---|---|
| Gson | Leer y escribir JSON (hablar con Ollama) |
| FlatLaf | El aspecto oscuro y moderno de Swing |
| sqlite-jdbc | La base de datos de los recordatorios |

## 5. Arranque automático con Windows

1. En Eclipse: clic derecho en el proyecto > `Run As > Maven build...` > en **Goals** escribe `package` > **Run**.
   Se crea `target/zero.jar`.
2. Doble clic en **`CONFIGURAR_ARRANQUE.bat`** y acepta el aviso de Windows.
3. Listo: Zero arranca solo al encender el PC, con permisos de administrador, y tienes un icono **Zero** en el escritorio.

**Cada vez que cambies el código** y quieras que el arranque automático use la nueva versión, repite el paso 1.
Para quitarlo: `QUITAR_ARRANQUE.bat`.

---

## 6. Mapa del código

Empieza a leer por **`Main.java`** y sigue este orden:

| Paquete | Clases | Qué hace | Qué vas a ver de Java |
|---|---|---|---|
| `es.zero` | `Main`, `Config`, `Ajustes` | Arranque, constantes, ajustes guardados | `static final`, text blocks, `Properties`, try-with-resources |
| `es.zero.nucleo` | `Nucleo`, `Evento`, `Estado` | El "cerebro de control": escucha, piensa, habla | Hilos (`Runnable`), colas, `enum`, `record`, regex |
| `es.zero.audio` | `Microfono`, `Voz` | Grabar el micro y reproducir la voz | `javax.sound`, productor/consumidor, `synchronized`, `wait/notify` |
| `es.zero.ia` | `Cerebro`, `ServicioIA` | Hablar con Ollama y con el servicio Python | `HttpClient`, JSON con Gson, streams, `ProcessBuilder` |
| `es.zero.herramientas` | `Herramienta`, `HerramientaBase` y una clase por acción | Lo que Zero sabe hacer | **Interfaces, clases abstractas, herencia, polimorfismo**, `Map` |
| `es.zero.recordatorios` | `Recordatorio`, `RepositorioRecordatorios`, `Recordatorios` | Recordatorios en SQLite | **JDBC**, `PreparedStatement`, SQL, capas (DAO + servicio), patrón observador |
| `es.zero.ui` | `Ventana`, `PanelChat`, `Orbe`... | La interfaz gráfica | Swing, layouts, `paintComponent`, `invokeLater`, lambdas |
| `es.zero.util` | `Fechas`, `Textos` | Ayudantes | `java.time`, `Pattern` |

### Recorrido de una orden

Cuando dices *"Zero, recuérdame mañana a las 5 llamar al dentista"*:

1. **`Microfono.grabarFrase()`** detecta que hablas (por volumen) y graba hasta que haces una pausa.
2. **`ServicioIA.transcribir()`** manda el audio a Python, que devuelve el texto.
3. **`Nucleo.extraerOrden()`** comprueba con una expresión regular que empieza por "Zero".
4. **`Cerebro.pensar()`** se lo manda a Ollama junto con la lista de herramientas.
5. El modelo responde: *"usa `crear_recordatorio` con texto=llamar al dentista, fecha_hora=2026-11-12 17:00"*.
6. **`RegistroHerramientas.ejecutar()`** busca esa herramienta en su `Map` y llama a su `ejecutar()`.
7. **`HerramientasRecordatorios.Crear`** → **`Recordatorios.crear()`** → **`RepositorioRecordatorios.crear()`** hace el `INSERT` en SQLite.
8. El repositorio avisa a sus oyentes y la **`Ventana`** repinta la lista de la derecha.
9. El resultado vuelve a Ollama, que redacta la respuesta, y cada frase va a **`Voz.encolar()`**.

Truco: pon un **punto de ruptura** (doble clic en el margen izquierdo de una línea) en
`RegistroHerramientas.ejecutar`, ejecuta con **Debug As > Java Application** y pídele algo a Zero.
Eclipse se parará ahí y podrás ver el valor de cada variable.

---

## 7. Retos para aprender (de fácil a difícil)

**Nivel 1 · Tocar sin miedo**
- Cambia el color `ACENTO` en `ui/Colores.java` y vuelve a ejecutar.
- Cambia la personalidad en `Config.PROMPT_SISTEMA`: que sea un pirata, que te tutee...
- Añade tus programas al mapa de `herramientas/AbrirAplicacion.java`.

**Nivel 2 · Tu primera herramienta.** Crea `herramientas/LanzarDado.java`:

```java
package es.zero.herramientas;

import java.util.Random;
import com.google.gson.JsonObject;

public class LanzarDado extends HerramientaBase {

    private final Random azar = new Random();

    public LanzarDado() {
        super("lanzar_dado", "Lanza un dado y dice el resultado.");
        parametroEntero("caras", "Número de caras del dado (6 si no se dice)", false);
    }

    @Override
    public String ejecutar(JsonObject argumentos) {
        int caras = entero(argumentos, "caras", 6);
        return "Ha salido un " + (azar.nextInt(caras) + 1) + ".";
    }
}
```

Regístrala en `Nucleo.crearHerramientas()` con `registro.registrar(new LanzarDado());`
y dile: *"Zero, lanza un dado de 20 caras"*. Fíjate en que **no has tocado nada más**:
eso es lo bueno del polimorfismo.

**Nivel 3 · Interfaz**
- Añade un botón *"Borrar todos"* encima de la lista de recordatorios.
- Haz que el chat tenga un botón para limpiar la conversación.

**Nivel 4 · Base de datos**
- Descarga *DB Browser for SQLite* y abre `datos/zero.db`. Mira la tabla y lanza consultas.
- Añade una columna `prioridad` (`ALTER TABLE`), súmala al `record Recordatorio`, al repositorio
  y a la herramienta `crear_recordatorio`. Haz que la interfaz pinte en rojo los de prioridad alta.

**Nivel 5 · Proyecto**
- Guarda las conversaciones en SQLite en vez de en ficheros `.txt`, con su propio repositorio.
- Crea una herramienta que te diga el tiempo usando una API pública con `HttpClient`.

---

## 8. Problemas típicos

| Pasa esto | Haz esto |
|---|---|
| *"Falta instalar el servicio de IA"* | Ejecuta `servicio-ia\INSTALAR_SERVICIO.bat` |
| *"El servicio de IA se ha cerrado"* | Mira `datos/servicio-ia.log` |
| *"No encuentro Ollama"* / *"Falta el modelo"* | Abre Ollama / ejecuta `ollama pull qwen3:8b` |
| No me hace caso al decir "Zero" | Mira en el chat *"oído, no era para mí: ..."* y añade esa forma a `Config.ALIAS_ACTIVACION` |
| Me corta las frases | Sube `Config.SEGUNDOS_SILENCIO` a `1.0` |
| Va lento | Panel SISTEMA: si el cerebro no está al 100% en la gráfica, usa `qwen3:4b` |
| Errores rojos en Eclipse al importar | Clic derecho en el proyecto > `Maven > Update Project...` |
| Se ha roto algo y no sé qué | Consola de Eclipse, o `datos/zero.log` si se arrancó solo |
