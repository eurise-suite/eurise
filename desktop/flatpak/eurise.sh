#!/bin/sh
# Avvio di Eurise dentro il Flatpak: lo stesso programma fa da finestra e, con un comando, da Ponte (eurise firefox…).
exec /app/lib/eurise/bin/eurise "$@"
