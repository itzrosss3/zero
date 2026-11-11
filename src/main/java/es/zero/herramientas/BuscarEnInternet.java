package es.zero.herramientas;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;

public class BuscarEnInternet extends HerramientaBase {

    public BuscarEnInternet() {
        super("buscar_en_internet", "Busca algo en Google y lo abre en el navegador.");
        parametroTexto("consulta", "Lo que hay que buscar", true);
    }

    @Override
    public String ejecutar(JsonObject argumentos) throws IOException {
        String consulta = texto(argumentos, "consulta", "");
        Comandos.abrirUrl("https://www.google.com/search?q=" + URLEncoder.encode(consulta, StandardCharsets.UTF_8));
        return "He abierto la búsqueda de '" + consulta + "' en el navegador.";
    }
}
