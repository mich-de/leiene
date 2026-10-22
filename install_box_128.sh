#!/usr/bin/env bash
set -e

IP="192.168.188.128"
PORT="5555"
APK_PATH="$(dirname "$0")/app-leiene.apk"

echo "=== Connessione al Box Android TV ($IP:$PORT) ==="
adb connect "$IP:$PORT" || true

echo "=== Installazione APK Le Iene sul Box ==="
adb -s "$IP:$PORT" install -r "$APK_PATH"

echo "=== Avvio Le Iene sul Box ==="
adb -s "$IP:$PORT" shell am start -n it.leiene.tv/.ui.MainActivity

echo "Fatto! App installata e avviata su $IP."
