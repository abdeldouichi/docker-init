#!/bin/sh
# Sélectionne l'application à exécuter : produce-pdf ou consume-pdf
set -e
case "$1" in
  produce-pdf|consume-pdf) exec java -jar "/app/$1.jar" ;;
  *) echo "Usage : produce-pdf | consume-pdf" >&2; exit 1 ;;
esac
