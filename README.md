# docker-init — Démo Docker : deux applications Java, un volume partagé

Démonstration  : deux conteneurs indépendants issus d'**une seule image** échangent un PDF via un **volume Docker nommé**.

## Prérequis

- Docker (testé avec Docker Engine récent), accès à Docker Hub (image `eclipse-temurin:17-jre`)
- `curl`
- Port **80** libre sur la machine hôte
- (Optionnel, pour recompiler les JAR) JDK 17

## Architecture

```
 ┌────────────────┐   écrit    ┌───────────────────────┐   lit     ┌──────────────────┐
 │ produce-pdf    │ ─────────► │ volume « pdf-data »   │ ◄──────── │ consume-pdf      │
 │ (s'arrête)     │            │ monté sur /app/output │           │ HTTP :80 (reste) │
 └────────────────┘            └───────────────────────┘           └────────┬─────────┘
                                                                     -p 80:80 │
                                                          http://localhost/consume-pdf
```

- **produce-pdf** : génère `/app/output/document.pdf` puis se termine. Il écrit d'abord `document.pdf.tmp`, puis le **renomme de façon atomique** : le consommateur ne voit donc jamais de fichier partiel.
- **consume-pdf** : serveur HTTP (JDK `com.sun.net.httpserver`) sur le port 80 du conteneur. `GET /consume-pdf` → `200` + `application/pdf`, ou `404` avec un message explicite si le PDF n'existe pas. Une erreur ne l'arrête jamais.
- **Volume ≠ port** : le *volume* (`-v pdf-data:/app/output`) sert à partager des **fichiers** ; le *port* (`-p 80:80`) sert à exposer un service **réseau** vers l'hôte. Les deux conteneurs ne communiquent pas entre eux : ils partagent seulement un dossier.
- **Un seul Dockerfile** : l'image contient les deux JAR (`COPY`) ; le point d'entrée `entrypoint.sh` choisit l'application selon l'argument (`produce-pdf` ou `consume-pdf`). C'est plus simple qu'un `ARG` de build, car une seule image est construite. `ARG JAVA_VERSION` permet d'ajuster la version de Java.

Hypothèse : les JAR fournis dans `jars/` sont ceux de ce dépôt (code minimal dans `src/`), compilés pour Java 17 ; le nom de fichier commun est `document.pdf` (variables `OUTPUT_DIR`, `PDF_NAME`).

## Arborescence

```
.
├── Dockerfile          # Dockerfile unique
├── entrypoint.sh       # choisit produce-pdf ou consume-pdf
├── build-jars.sh       # recompile les JAR (optionnel)
├── jars/
│   ├── produce-pdf.jar
│   └── consume-pdf.jar
├── src/
│   ├── produce-pdf/ProducePdf.java
│   └── consume-pdf/ConsumePdf.java
└── README.md
```

## Utilisation

```bash
# 1. Construire l'image unique
docker build -t java-pdf-demo .

# 2. Générer le PDF (le conteneur s'arrête ensuite)
docker run --rm -v pdf-data:/app/output java-pdf-demo produce-pdf

# 3. Démarrer le consommateur HTTP
docker run -d --name consume-pdf -p 80:80 -v pdf-data:/app/output java-pdf-demo consume-pdf
```

### Tests

```bash
curl -i http://localhost/consume-pdf                 # 200, Content-Type: application/pdf
curl -o recu.pdf http://localhost/consume-pdf && file recu.pdf
```

Navigateur : ouvrir <http://localhost/consume-pdf> (téléchargement du PDF).

### Cas « PDF absent » (404)

```bash
docker rm -f consume-pdf
docker volume rm pdf-data            # volume vide
docker run -d --name consume-pdf -p 80:80 -v pdf-data:/app/output java-pdf-demo consume-pdf
curl -i http://localhost/consume-pdf # 404 Not Found + message explicite
docker run --rm -v pdf-data:/app/output java-pdf-demo produce-pdf
curl -i http://localhost/consume-pdf # 200 sans redémarrer le serveur
```

### Nettoyage

```bash
docker rm -f consume-pdf
docker volume rm pdf-data
docker rmi java-pdf-demo
```

Le volume `pdf-data` conserve le PDF même après la suppression du conteneur producteur ; il n'est effacé que par `docker volume rm`.

## Scénario de démonstration

| Étape | Commande | Résultat attendu |
|---|---|---|
| 1 | `docker build -t java-pdf-demo .` | Image construite |
| 2 | `docker run -d --name consume-pdf -p 80:80 -v pdf-data:/app/output java-pdf-demo consume-pdf` | Conteneur actif (`docker ps`) |
| 3 | `curl -i http://localhost/consume-pdf` | `404 Not Found` (pas encore de PDF) |
| 4 | `docker run --rm -v pdf-data:/app/output java-pdf-demo produce-pdf` | « PDF genere : /app/output/document.pdf », le conteneur disparaît |
| 5 | `curl -i http://localhost/consume-pdf` | `200`, `application/pdf` |
| 6 | `docker volume inspect pdf-data` | Le volume existe toujours (persistance) |
| 7 | Nettoyage (voir ci-dessus) | Tout est supprimé |

## Problèmes fréquents

- **`port is already allocated` / `address already in use`** : le port 80 est occupé ; arrêter le service concerné ou utiliser `-p 8080:80` puis `http://localhost:8080/consume-pdf`.
- **`The container name "/consume-pdf" is already in use`** : `docker rm -f consume-pdf`.
- **404 alors que le producteur a tourné** : le producteur et le consommateur doivent utiliser le **même nom de volume** (`pdf-data`) et le même chemin `/app/output`.
- **Permissions du volume** : les conteneurs s'exécutent en `root`, ce qui évite les soucis avec un volume nommé (et permet d'écouter sur le port 80). Avec un utilisateur non-root ou un dossier hôte (bind mount), il faut adapter les droits (`chown`/`--user`) et utiliser un port > 1024.
- **Sous Linux, `permission denied` sur le port 80 hors Docker** : exécuter via Docker, ou définir `PORT=8080`.
