import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Génère un PDF minimal puis publie le fichier de façon atomique. */
public class ProducePdf {
    public static void main(String[] args) throws IOException {
        Path dir = Paths.get(System.getenv().getOrDefault("OUTPUT_DIR", "/app/output"));
        String name = System.getenv().getOrDefault("PDF_NAME", "document.pdf");
        Files.createDirectories(dir);
        Path tmp = dir.resolve(name + ".tmp");
        Path target = dir.resolve(name);
        Files.write(tmp, buildPdf("Bonjour depuis produce-pdf ! Genere le " + java.time.Instant.now()));
        // Renommage atomique : le consommateur ne voit jamais un fichier partiel
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        System.out.println("PDF genere : " + target);
    }

    static byte[] buildPdf(String text) {
        String safe = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        String stream = "BT /F1 18 Tf 50 750 Td (" + safe + ") Tj ET";
        String[] objs = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
            "<< /Length " + stream.length() + " >>\nstream\n" + stream + "\nendstream",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n");
        for (int i = 0; i < objs.length; i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objs[i] + "\nendobj\n");
        }
        int xref = out.size();
        write(out, "xref\n0 " + (objs.length + 1) + "\n0000000000 65535 f \n");
        for (int o : offsets) write(out, String.format("%010d 00000 n \n", o));
        write(out, "trailer\n<< /Size " + (objs.length + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    static void write(ByteArrayOutputStream o, String s) {
        byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
        o.write(b, 0, b.length);
    }
}
