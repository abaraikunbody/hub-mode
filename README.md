# Hub Mode pour Pixel

Écran de veille Android façon « mode Hub / StandBy » : **heure, date, Spotify et cours d'ETF**
quand le téléphone est **verrouillé, en charge et à l'horizontale**.

## Installer
1. Ouvre **Releases › latest** et télécharge `HubMode.apk` depuis le téléphone.
2. Ouvre le fichier et autorise l'installation depuis le navigateur si demandé.

## Configurer (dans l'appli Hub Mode)
1. **Ouvrir les réglages d'écran de veille** → choisir *Hub Mode*, puis *Quand démarrer* → *Pendant la charge*.
2. **Autoriser l'accès aux notifications** (pour Spotify). Si c'est grisé : *Infos de l'appli* › ⋮ › *Autoriser les paramètres restreints*.
3. Mettre ses ETF au format Yahoo Finance, un par ligne : `DCAM.PA|PEA Monde`.
4. **Aperçu** pour vérifier.

La compilation est automatique (GitHub Actions) à chaque modification du code.
Les cours viennent de l'API publique non officielle de Yahoo Finance (différé possible).
