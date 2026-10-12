ARG JAVA_VERSION=17
FROM eclipse-temurin:${JAVA_VERSION}-jre

WORKDIR /app
COPY jars/produce-pdf.jar jars/consume-pdf.jar /app/
COPY entrypoint.sh /app/entrypoint.sh

# Dossier partagé (monté comme volume nommé) et chemin commun aux deux applications
ENV OUTPUT_DIR=/app/output PDF_NAME=document.pdf
RUN chmod +x /app/entrypoint.sh && mkdir -p /app/output
VOLUME /app/output
EXPOSE 80

ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["consume-pdf"]
