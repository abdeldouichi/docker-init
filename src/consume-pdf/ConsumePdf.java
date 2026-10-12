import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Serveur HTTP qui sert le PDF du volume partagé sur /consume-pdf. */
public class ConsumePdf {
    public static void main(String[] args) throws IOException {
        Path pdf = Paths.get(System.getenv().getOrDefault("OUTPUT_DIR", "/app/output"))
                .resolve(System.getenv().getOrDefault("PDF_NAME", "document.pdf"));
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "80"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/consume-pdf", ex -> handle(ex, pdf));
        server.start();
        System.out.println("consume-pdf en ecoute sur le port " + port + ", fichier : " + pdf);
    }

    static void handle(HttpExchange ex, Path pdf) throws IOException {
        try {
            if (!"GET".equals(ex.getRequestMethod())) {
                send(ex, 405, "text/plain; charset=utf-8", "Methode non autorisee".getBytes(StandardCharsets.UTF_8));
            } else {
                byte[] data;
                try {
                    // Le producteur publie par renommage atomique : le fichier est toujours complet
                    data = Files.readAllBytes(pdf);
                } catch (NoSuchFileException e) {
                    send(ex, 404, "text/plain; charset=utf-8",
                        ("404 Not Found : le PDF " + pdf + " n'existe pas. Lancez d'abord produce-pdf.\n")
                            .getBytes(StandardCharsets.UTF_8));
                    return;
                }
                ex.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + pdf.getFileName() + "\"");
                send(ex, 200, "application/pdf", data);
            }
        } catch (Exception e) {
            e.printStackTrace();
            try { send(ex, 500, "text/plain; charset=utf-8", "Erreur interne\n".getBytes(StandardCharsets.UTF_8)); }
            catch (Exception ignored) { }
        } finally {
            ex.close();
        }
    }

    static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }
}
