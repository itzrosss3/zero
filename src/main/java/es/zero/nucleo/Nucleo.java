package es.zero.nucleo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import es.zero.Ajustes;
import es.zero.Config;
import es.zero.audio.Microfono;
import es.zero.audio.Voz;
import es.zero.audio.VozDisponible;
import es.zero.herramientas.AbrirAplicacion;
import es.zero.herramientas.AbrirWeb;
import es.zero.herramientas.Asistente;
import es.zero.herramientas.BuscarEnInternet;
import es.zero.herramientas.EstadoSistema;
import es.zero.herramientas.FechaHora;
import es.zero.herramientas.HerramientasProgramas;
import es.zero.herramientas.HerramientasRecordatorios;
import es.zero.herramientas.Notas;
import es.zero.herramientas.RegistroHerramientas;
import es.zero.herramientas.Temporizador;
import es.zero.ia.Cerebro;
import es.zero.ia.ServicioIA;
import es.zero.recordatorios.Recordatorio;
import es.zero.recordatorios.Recordatorios;
import es.zero.util.Textos;

/**
 * El motor de Zero: escucha, piensa y habla. Funciona en su propio hilo (implementa Runnable)
 * para que la ventana nunca se congele mientras Zero trabaja.
 * <p>
 * Se comunica con la interfaz SOLO mediante eventos ({@link Evento}): no sabe nada de Swing.
 * Si mañana quisieras otra interfaz (web, consola...), el núcleo no cambiaría.
 */
public class Nucleo implements Runnable, Asistente {

    // "Zero, ..." / "Oye cero ..." -> grupo 2 es la orden. UNICODE_CHARACTER_CLASS: \w incluye tildes
    private static final Pattern PATRON_NOMBRE = Pattern.compile(
            "^\\W*(?:(?:hey|oye|ey|hola|vale|bueno)\\W+)?(" + String.join("|", Config.ALIAS_ACTIVACION)
                    + ")\\b\\W*(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern SI = Pattern.compile(
            "\\b(s[ií]|vale|adelante|confirmo|confirmado|hazlo|claro|dale|ok)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern NO = Pattern.compile(
            "\\b(no|cancela|cancelar|para|espera|nada)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private final Ajustes ajustes;
    private final Recordatorios recordatorios;
    private final boolean arranqueConWindows;
    private Consumer<Evento> oyente = e -> { }; // por defecto no hace nada

    private final ServicioIA servicio = new ServicioIA();
    private Voz voz;
    private Microfono micro;
    private Cerebro cerebro;

    private final BlockingQueue<String> entradaTexto = new LinkedBlockingQueue<>();
    private final AtomicBoolean interrumpir = new AtomicBoolean(false);
    private volatile boolean micActivo;
    private final AtomicBoolean confirmacionRespondida = new AtomicBoolean(false);
    private volatile boolean confirmacionValor;
    private Estado ultimoEstado;

    private final ScheduledExecutorService vigilante = Executors.newSingleThreadScheduledExecutor(tarea -> {
        Thread hilo = new Thread(tarea, "vigilante-recordatorios");
        hilo.setDaemon(true);
        return hilo;
    });

    public Nucleo(Ajustes ajustes, Recordatorios recordatorios, boolean arranqueConWindows) {
        this.ajustes = ajustes;
        this.recordatorios = recordatorios;
        this.arranqueConWindows = arranqueConWindows;
        this.micActivo = ajustes.micro();
    }

    public void ponerOyente(Consumer<Evento> oyente) {
        this.oyente = oyente;
    }

    public Ajustes ajustes() {
        return ajustes;
    }

    // =========================================================================
    // Comunicación con la interfaz
    // =========================================================================

    private void emitir(Evento.Tipo tipo, Object dato) {
        oyente.accept(new Evento(tipo, dato));
        if (tipo != Evento.Tipo.ESTADO && tipo != Evento.Tipo.ZERO_TROZO && tipo != Evento.Tipo.ZERO_FIN
                && tipo != Evento.Tipo.SISTEMA) {
            System.out.println("[" + LocalTime.now().withNano(0) + "] [" + tipo + "] " + (dato == null ? "" : dato));
        }
    }

    private void emitir(Evento.Tipo tipo) {
        emitir(tipo, null);
    }

    private void estado(Estado nuevo) {
        if (nuevo != ultimoEstado) { // solo avisa si cambia
            ultimoEstado = nuevo;
            emitir(Evento.Tipo.ESTADO, nuevo);
        }
    }

    private void guardarConversacion(String quien, String texto) {
        if (texto == null || texto.isBlank()) {
            return;
        }
        try {
            Files.createDirectories(Config.CARPETA_CONVERSACIONES);
            String linea = "[" + LocalTime.now().withNano(0) + "] " + quien + ": " + texto + System.lineSeparator();
            Files.writeString(Config.CARPETA_CONVERSACIONES.resolve(LocalDate.now() + ".txt"), linea,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // no es grave: la conversación sigue aunque no se guarde
        }
    }

    // =========================================================================
    // Órdenes que llegan desde la interfaz
    // =========================================================================

    public void enviarTexto(String texto) {
        entradaTexto.add(texto);
        interrumpir.set(true); // deja de escuchar el micro para atender el texto
    }

    public boolean micActivo() {
        return micActivo;
    }

    public void ponerMicro(boolean activo) {
        micActivo = activo;
        if (micro != null) {
            micro.ponerActivo(activo);
        }
        interrumpir.set(true);
        ajustes.ponerMicro(activo);
    }

    public void responderConfirmacion(boolean valor) {
        confirmacionValor = valor;
        confirmacionRespondida.set(true);
    }

    public void cambiarVoz(VozDisponible nueva) {
        ajustes.ponerVoz(nueva);
        if (voz != null) {
            voz.ponerVoz(nueva);
            voz.encolar(Config.FRASE_PRUEBA_VOZ);
        }
    }

    public void probarVoz() {
        if (voz != null) {
            voz.encolar(Config.FRASE_PRUEBA_VOZ);
        }
    }

    public void ponerVelocidad(double velocidad) {
        ajustes.ponerVelocidad(velocidad);
        if (voz != null) {
            voz.ponerVelocidad(velocidad);
        }
    }

    public void ponerVolumen(double volumen) {
        ajustes.ponerVolumen(volumen);
        if (voz != null) {
            voz.ponerVolumen(volumen);
        }
    }

    // =========================================================================
    // Implementación de Asistente (lo que usan las herramientas)
    // =========================================================================

    /** Dice una frase completa. Se puede llamar desde cualquier hilo. */
    @Override
    public void hablar(String texto) {
        emitir(Evento.Tipo.ZERO, texto);
        guardarConversacion(Config.NOMBRE_ASISTENTE, texto);
        if (voz != null) {
            try {
                voz.decir(texto);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void avisar(String texto) {
        emitir(Evento.Tipo.INFO, texto);
    }

    @Override
    public boolean confirmar(String pregunta) {
        confirmacionRespondida.set(false);
        confirmacionValor = false;
        emitir(Evento.Tipo.CONFIRMAR, pregunta);
        estado(Estado.CONFIRMANDO);
        try {
            voz.decir(pregunta.lines().findFirst().orElse(pregunta) + " Diga sí o no.");
            long fin = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < fin && !confirmacionRespondida.get()) {
                if (micro != null && micActivo) {
                    double restante = (fin - System.currentTimeMillis()) / 1000.0;
                    byte[] audio = micro.grabarFrase(restante, confirmacionRespondida);
                    if (audio == null) {
                        continue;
                    }
                    String respuesta = transcribir(audio);
                    if (respuesta.isEmpty()) {
                        continue;
                    }
                    emitir(Evento.Tipo.USUARIO, respuesta);
                    if (SI.matcher(respuesta).find()) {
                        responderConfirmacion(true);
                    } else if (NO.matcher(respuesta).find()) {
                        responderConfirmacion(false);
                    }
                } else {
                    Thread.sleep(300);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        emitir(Evento.Tipo.CONFIRMAR_FIN);
        estado(Estado.PENSANDO);
        return confirmacionRespondida.get() && confirmacionValor;
    }

    // =========================================================================
    // Arranque
    // =========================================================================

    @Override
    public void run() {
        try {
            arrancar();
            bucle();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            String mensaje = e.getMessage() != null ? e.getMessage() : e.toString();
            emitir(Evento.Tipo.ERROR, mensaje);
            estado(Estado.ERROR);
            e.printStackTrace();
        }
    }

    private void arrancar() throws Exception {
        estado(Estado.CARGANDO);
        if (arranqueConWindows) {
            avisar("Esperando a que Windows termine de arrancar...");
            Thread.sleep(Config.ESPERA_ARRANQUE_PC_SEGUNDOS * 1000L);
        }

        avisar("Arrancando el servicio de IA (oído y voz)... la primera vez tarda un poco.");
        servicio.arrancar(this::avisar);
        voz = new Voz(servicio, ajustes.voz(), ajustes.velocidad(), ajustes.volumen());

        avisar("Cargando el cerebro en la gráfica...");
        cerebro = new Cerebro(crearHerramientas());
        cerebro.preparar();

        prepararMicrofono();
        informarSistema();

        int hora = LocalTime.now().getHour();
        String saludo = hora >= 6 && hora < 14 ? "Buenos días" : hora >= 14 && hora < 21 ? "Buenas tardes" : "Buenas noches";
        hablar(saludo + ", " + Config.NOMBRE_USUARIO + ". " + Config.NOMBRE_ASISTENTE + " en línea.");
        hablar(recordatorios.resumenArranque().orElse("No tiene recordatorios pendientes."));

        // Cada 20 segundos mira si ha llegado la hora de algún recordatorio
        vigilante.scheduleAtFixedRate(this::revisarRecordatorios,
                Config.REVISAR_RECORDATORIOS_SEGUNDOS, Config.REVISAR_RECORDATORIOS_SEGUNDOS, TimeUnit.SECONDS);
    }

    /** Aquí se registran todas las herramientas. ¿Has creado una nueva? Añádela aquí. */
    private RegistroHerramientas crearHerramientas() {
        RegistroHerramientas registro = new RegistroHerramientas();
        registro.registrar(new HerramientasRecordatorios.Crear(recordatorios));
        registro.registrar(new HerramientasRecordatorios.Listar(recordatorios));
        registro.registrar(new HerramientasRecordatorios.Borrar(recordatorios));
        registro.registrar(new FechaHora());
        registro.registrar(new AbrirAplicacion());
        registro.registrar(new BuscarEnInternet());
        registro.registrar(new AbrirWeb());
        registro.registrar(new EstadoSistema());
        registro.registrar(new Temporizador(this));
        registro.registrar(new Notas.Tomar());
        registro.registrar(new Notas.Leer());
        registro.registrar(new HerramientasProgramas.Buscar());
        registro.registrar(new HerramientasProgramas.Instalar(this));
        registro.registrar(new HerramientasProgramas.Desinstalar(this));
        return registro;
    }

    private void prepararMicrofono() throws InterruptedException {
        Exception ultimo = null;
        for (int intento = 0; intento < 10 && micro == null; intento++) { // al encender el PC puede tardar
            try {
                micro = new Microfono();
            } catch (Exception e) {
                ultimo = e;
                Thread.sleep(3000);
            }
        }
        if (micro == null) {
            emitir(Evento.Tipo.AVISO, "No hay micrófono disponible (" + ultimo.getMessage() + "). Puedes escribirme abajo.");
            return;
        }
        estado(Estado.CALIBRANDO);
        micro.calibrar();
        micro.ponerActivo(micActivo);
    }

    private void informarSistema() {
        String oido = servicio.descripcionOido();
        int gpu = cerebro.porcentajeGpu();
        String textoCerebro = gpu >= 0 ? Config.LLM_MODELO + " · " + gpu + "% en gráfica" : Config.LLM_MODELO;
        emitir(Evento.Tipo.SISTEMA, new Evento.InfoSistema(oido, servicio.descripcionVoz(), textoCerebro,
                micro != null ? "Disponible" : "No disponible"));

        if (oido.contains("procesador")) {
            emitir(Evento.Tipo.AVISO, "El oído funciona con el procesador, no con la gráfica. Actualiza el driver de NVIDIA.");
        }
        if (gpu >= 0 && gpu < 95) {
            emitir(Evento.Tipo.AVISO, "El cerebro solo cabe un " + gpu + "% en la gráfica y por eso va más lento. "
                    + "Comprueba con \"ollama ps\" en PowerShell que no hay otros modelos cargados, "
                    + "o usa uno más pequeño en LLM_MODELO (Config.java).");
        }
    }

    private void revisarRecordatorios() {
        try {
            for (Recordatorio r : recordatorios.vencidos()) {
                hablar(Character.toUpperCase(Config.NOMBRE_USUARIO.charAt(0)) + Config.NOMBRE_USUARIO.substring(1)
                        + ", le recuerdo: " + r.texto() + ".");
            }
        } catch (Exception e) {
            System.err.println("[!] Error revisando recordatorios: " + e.getMessage());
        }
    }

    // =========================================================================
    // Bucle principal
    // =========================================================================

    private void bucle() throws InterruptedException {
        while (true) {
            // 1) ¿Has escrito algo?
            String escrito = entradaTexto.poll();
            if (escrito != null) {
                interrumpir.set(false);
                atender(escrito, false);
                continue;
            }

            // 2) Sin micro o silenciado: solo esperamos texto
            if (micro == null || !micActivo) {
                estado(micro == null ? Estado.SIN_MICRO : Estado.SILENCIADO);
                escrito = entradaTexto.poll(300, TimeUnit.MILLISECONDS);
                if (escrito != null) {
                    interrumpir.set(false);
                    atender(escrito, false);
                }
                continue;
            }

            // 3) Escuchar hasta que alguien hable
            estado(Estado.ESPERA);
            byte[] audio = micro.grabarFrase(Microfono.SIN_LIMITE, interrumpir);
            interrumpir.set(false);
            if (audio == null) {
                continue;
            }
            estado(Estado.ENTENDIENDO);
            String texto = transcribir(audio);
            String orden = extraerOrden(texto);
            if (orden == null) {
                if (!texto.isEmpty()) {
                    emitir(Evento.Tipo.IGNORADO, texto);
                }
                continue;
            }

            if (orden.isEmpty()) { // solo ha dicho "Zero": pitido y escucha la orden
                voz.pitido(880);
                estado(Estado.ESCUCHANDO);
                audio = micro.grabarFrase(Config.ESPERA_INICIO, interrumpir);
                if (audio == null) {
                    voz.pitido(440);
                    continue;
                }
                estado(Estado.ENTENDIENDO);
                orden = transcribir(audio);
                if (orden.isEmpty()) {
                    continue;
                }
            }
            atender(orden, true);
        }
    }

    /** Atiende una orden y, si fue por voz, sigue escuchando unos segundos (modo conversación). */
    private void atender(String orden, boolean porVoz) throws InterruptedException {
        while (orden != null && !orden.isEmpty()) {
            emitir(Evento.Tipo.USUARIO, orden);
            guardarConversacion("Tú", orden);
            if (esSalida(orden)) {
                hablar("Desconectando. Hasta pronto, " + Config.NOMBRE_USUARIO + ".");
                emitir(Evento.Tipo.SALIR);
                return;
            }

            estado(Estado.PENSANDO);
            List<String> partes = new ArrayList<>();
            // Clase anónima que implementa la interfaz Oyente del cerebro
            Cerebro.Oyente oyenteCerebro = new Cerebro.Oyente() {
                @Override
                public void frase(String frase) {
                    String limpia = Textos.limpiar(frase);
                    if (!limpia.isEmpty()) {
                        partes.add(limpia);
                        emitir(Evento.Tipo.ZERO_TROZO, limpia);
                        estado(Estado.HABLANDO);
                        voz.encolar(limpia);
                    }
                }

                @Override
                public void herramienta(String nombre, String resultado) {
                    emitir(Evento.Tipo.HERRAMIENTA, nombre + ": " + resultado);
                }
            };

            try {
                cerebro.pensar(orden, oyenteCerebro);
            } catch (IOException e) {
                oyenteCerebro.frase("Tengo un problema con mi cerebro, " + Config.NOMBRE_USUARIO + ": " + e.getMessage());
            }
            emitir(Evento.Tipo.ZERO_FIN);
            guardarConversacion(Config.NOMBRE_ASISTENTE, String.join(" ", partes));
            voz.esperar();

            if (!porVoz || !Config.MODO_CONVERSACION || micro == null || !micActivo) {
                return;
            }
            estado(Estado.ESCUCHANDO);
            byte[] audio = micro.grabarFrase(Config.ESPERA_CONVERSACION, interrumpir);
            if (audio == null) {
                return;
            }
            estado(Estado.ENTENDIENDO);
            String siguiente = transcribir(audio);
            String sinNombre = extraerOrden(siguiente);
            orden = (sinNombre != null && !sinNombre.isEmpty()) ? sinNombre : siguiente;
        }
    }

    private String transcribir(byte[] audio) throws InterruptedException {
        try {
            return servicio.transcribir(audio);
        } catch (IOException e) {
            emitir(Evento.Tipo.ERROR, "No he podido entenderte: " + e.getMessage());
            return "";
        }
    }

    // =========================================================================
    // Utilidades
    // =========================================================================

    /**
     * Si la frase empieza por el nombre, devuelve lo que viene después ("" si solo dijo el nombre).
     * Si no va dirigida a Zero, devuelve null.
     */
    public static String extraerOrden(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        Matcher m = PATRON_NOMBRE.matcher(texto.strip());
        return m.matches() ? m.group(2).strip() : null;
    }

    private static boolean esSalida(String texto) {
        String t = texto.toLowerCase();
        return Config.PALABRAS_SALIDA.stream().anyMatch(t::contains);
    }
}
