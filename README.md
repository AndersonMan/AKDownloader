# AKDownloader

Application Android personnelle de téléchargement multimédia (musique, vidéo, podcasts…) avec détection automatique des médias et lecteur intégré.

> ⚠️ Usage personnel uniquement. Ne téléchargez que des contenus dont vous avez le droit : fichiers libres de droits, Creative Commons, domaines publics, podcasts, archives ouvertes. Les plateformes à DRM ou à contenu protégé (YouTube, Spotify, Netflix…) ne sont pas — et ne seront pas — prises en charge.

## Fonctionnalités

- **Accueil** : collez un lien de fichier direct (`.mp3`, `.mp4`, `.mkv`, `.webm`, `.flac`, `.m3u8`…) ou l'adresse d'une page web ; l'app analyse la page et liste tous les médias exposés publiquement.
- **Navigateur intégré** : navigation normale ; chaque média (audio/vidéo/flux HLS/DASH) présent sur les pages visitées est détecté automatiquement (interception réseau + analyse du DOM) et proposé dans un panneau « Médias détectés ».
- **Téléchargeur multi-connexions** : jusqu'à 4 requêtes HTTP Range en parallèle par fichier, reprise automatique des segments en cas d'erreur réseau, 3 téléchargements simultanés, notifications de progression, annulation / relance.
- **Enregistrement** : fichiers enregistrés via MediaStore dans `Download/AKDownloader` (visibles dans n'importe quel gestionnaire de fichiers), sans permission de stockage à accorder sur Android 10+.
- **Bibliothèque** : balayage automatique des audios et vidéos présents sur l'appareil (y compris fichiers déposés par d'autres apps, sans extension visible), aussi bien ceux d'AKDownloader que le reste du stockage public.
- **Lecteur intégré** : lecture audio/vidéo/flux HLS via Media3 ExoPlayer, avec contrôles complets.

## Stack technique

- Kotlin 2.0, Jetpack Compose (Material 3)
- OkHttp 4 (téléchargements multi-segments)
- Room (historique de téléchargements)
- Media3 / ExoPlayer 1.5 (lecture, y compris HLS)
- Min SDK 29 (Android 10) · Cible SDK 35

## Compilation

Ouvrez le dossier dans **Android Studio** (Ladybug ou plus récent) : le wrapper Gradle 8.11.1 télécharge tout automatiquement, puis lancez `Run ▶`.

En ligne de commande :

```bash
./gradlew assembleDebug
# APK : app/build/outputs/apk/debug/app-debug.apk
```

## Idées d'amélioration (v2)

- Sélecteur de qualité pour les flux HLS/DASH (piste vidéo, bitrate, langue audio)
- Téléchargement en arrière-plan avec reprise après redémarrage de l'app
- File d'attente avec priorités et limiteeur de débit
- Sous-titres, équilibriseur de lecture, reprise de lecture exacte
