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
 │ produce-pdf    │ ─────────► │   ./output (hôte)     │ ◄──────── │ consume-pdf      │
 │ (s'arrête)     │            │ monté sur /app/output │           │ HTTP :80 (reste) │
 └────────────────┘            └───────────────────────┘           └────────┬─────────┘
                                                                     -p 80:80 │
                                                          http://localhost/consume-pdf
```

- **produce-pdf** : génère `/app/output/document.pdf` puis se termine. Il écrit d'abord `document.pdf.tmp`, puis le **renomme de façon atomique** : le consommateur ne voit donc jamais de fichier partiel.
- **consume-pdf** : serveur HTTP (JDK `com.sun.net.httpserver`) sur le port 80 du conteneur. `GET /consume-pdf` → `200` + `application/pdf`, ou `404` avec un message explicite si le PDF n'existe pas. Une erreur ne l'arrête jamais.
- **Volume ≠ port** : le *volume* (`-v ./output:/app/output`) sert à partager des **fichiers** ; le *port* (`-p 80:80`) sert à exposer un service **réseau** vers l'hôte. Les deux conteneurs ne communiquent pas entre eux : ils partagent seulement un dossier. Ici, c'est un **bind mount** vers le dossier `output/` du dépôt (plutôt qu'un volume nommé Docker) : le PDF généré est directement visible et inspectable depuis la machine hôte.
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
├── output/             # dossier partagé (bind mount) avec les conteneurs, contient document.pdf après génération
│   └── .gitkeep
├── src/
│   ├── produce-pdf/ProducePdf.java
│   └── consume-pdf/ConsumePdf.java
└── README.md
```

## Utilisation

```bash
# 1. Construire l'image unique
docker build -t java-pdf-demo .

# 2. Générer le PDF dans ./output (le conteneur s'arrête ensuite)
docker run --rm -v "$(pwd)/output:/app/output" java-pdf-demo produce-pdf

# 3. Démarrer le consommateur HTTP
docker run -d --name consume-pdf -p 80:80 -v "$(pwd)/output:/app/output" java-pdf-demo consume-pdf
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
rm -f output/document.pdf*           # dossier vide (hors .gitkeep)
docker run -d --name consume-pdf -p 80:80 -v "$(pwd)/output:/app/output" java-pdf-demo consume-pdf
curl -i http://localhost/consume-pdf # 404 Not Found + message explicite
docker run --rm -v "$(pwd)/output:/app/output" java-pdf-demo produce-pdf
curl -i http://localhost/consume-pdf # 200 sans redémarrer le serveur
```

### Nettoyage

```bash
docker rm -f consume-pdf
rm -f output/document.pdf*
docker rmi java-pdf-demo
```

Le dossier `output/` (bind mount) conserve le PDF même après la suppression du conteneur producteur ; il n'est effacé qu'en supprimant le fichier manuellement sur l'hôte.

## Scénario de démonstration

| Étape | Commande | Résultat attendu |
|---|---|---|
| 1 | `docker build -t java-pdf-demo .` | Image construite |
| 2 | `docker run -d --name consume-pdf -p 80:80 -v "$(pwd)/output:/app/output" java-pdf-demo consume-pdf` | Conteneur actif (`docker ps`) |
| 3 | `curl -i http://localhost/consume-pdf` | `404 Not Found` (pas encore de PDF) |
| 4 | `docker run --rm -v "$(pwd)/output:/app/output" java-pdf-demo produce-pdf` | « PDF genere : /app/output/document.pdf », le conteneur disparaît, `output/document.pdf` visible sur l'hôte |
| 5 | `curl -i http://localhost/consume-pdf` | `200`, `application/pdf` |
| 6 | `ls -l output/` | Le fichier `document.pdf` existe toujours (persistance) |
| 7 | Nettoyage (voir ci-dessus) | Tout est supprimé |

## Problèmes fréquents

- **`port is already allocated` / `address already in use`** : le port 80 est occupé ; arrêter le service concerné ou utiliser `-p 8080:80` puis `http://localhost:8080/consume-pdf`.
- **`The container name "/consume-pdf" is already in use`** : `docker rm -f consume-pdf`.
- **404 alors que le producteur a tourné** : le producteur et le consommateur doivent monter le **même dossier hôte** (`./output`) sur le même chemin `/app/output`.
- **Permissions du volume** : les conteneurs s'exécutent en `root`, ce qui évite les soucis avec un bind mount (et permet d'écouter sur le port 80). Avec un utilisateur non-root, il faut adapter les droits (`chown`/`--user`) sur `output/` et utiliser un port > 1024.
- **Sous Linux, `permission denied` sur le port 80 hors Docker** : exécuter via Docker, ou définir `PORT=8080`.
