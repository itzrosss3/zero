package es.zero.herramientas;

import java.io.IOException;

import com.google.gson.JsonObject;

public class AbrirWeb extends HerramientaBase {

    public AbrirWeb() {
        super("abrir_web", "Abre una página web concreta.");
        parametroTexto("url", "Dirección, p. ej. youtube.com", true);
    }

    @Override
    public String ejecutar(JsonObject argumentos) throws IOException {
        String url = texto(argumentos, "url", "");
        if (!url.startsWith("http")) {
            url = "https://" + url;
        }
        Comandos.abrirUrl(url);
        return "Abriendo " + url + ".";
    }
}
