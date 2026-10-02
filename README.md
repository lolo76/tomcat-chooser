# Tomcat Chooser

Petite application Windows, Mac et Linux (JavaFX, Java 25) qui liste les `<Context>` d'un `server.xml`
Tomcat et permet de les **commenter / décommenter d'un clic**.

![Capture](docs/capture.png)

## Fonctionnement

- Au démarrage, le fichier ouvert est `C:\Tomcat70\conf\server.xml`
  (ou le dernier fichier choisi, mémorisé automatiquement).
- L'interface suit le style **Material** : barre d'application bleue (« Choisi ton projet »), deux cartes blanches à ombre douce,
  champs soulignés, boutons-icônes ronds avec infobulle, boîtes de dialogue à en-tête coloré.
- En haut à droite de la barre bleue, un bouton choisit le **thème** : clair (soleil), sombre (lune) ou celui du
  système (demi-disque, qui suit Windows et ses changements). Un clic passe au suivant ; le choix est mémorisé.
- La première carte regroupe le chemin du `server.xml` et Tomcat.
  À droite du chemin, deux icônes : le dossier choisit un autre `server.xml` (on peut aussi taper
  le chemin puis Entrée), la feuille ouvre `server.xml` dans l'éditeur du système (Bloc-notes à défaut).
- Tout est actualisé toutes les 3 s : l'état de Tomcat, et la liste si `server.xml` a changé sur le disque
  (modifié à la main ou par un autre outil). La ligne sélectionnée et la recherche sont conservées.
- Sous le chemin, dans la même carte, l'état et les commandes de **Tomcat** : libellé à gauche, boutons à droite
  (Tomcat démarré / arrêté, en petit). Il teste sur localhost le port HTTP du `server.xml` choisi
  (jamais le port d'arrêt), quel que soit le lanceur (Eclipse, startup.bat, service).
  - ▶ / ■ **démarre** ou **arrête** Tomcat, ↻ le **redémarre** (arrêt, puis démarrage).
  - **Avec Eclipse (recommandé)** : si le plugin *Eclipse MCP Server* (vogella) est actif et que le plugin
    *Sysdeo Tomcat Launcher* pilote le Tomcat de ce `server.xml` (préférence `tomcatConfigFile`), les boutons
    exécutent les commandes Sysdeo « Démarrer Tomcat » et « Arrêter Tomcat » dans Eclipse : console et
    débogueur d'Eclipse comme d'habitude. Les infobulles indiquent « avec Sysdeo (dans Eclipse) ».
    Prérequis : Eclipse ouvert, serveur MCP activé (*Preferences > General > MCP Server*, port 8642 par défaut,
    changeable par `-Dtomcatchooser.mcpPort=…`), token lu dans `~/.eclipse/com.vogella.eclipse.mcp.server/token`.
    Tant que Tomcat finit de démarrer (plus d'une minute pour Areo), Sysdeo perd l'ordre d'arrêt : l'application
    le renvoie toutes les 10 s jusqu'à l'arrêt effectif (4 minutes au plus).
  - **Sans Eclipse** (autre `server.xml`, Eclipse fermé ou plugin absent) : l'arrêt envoie la commande d'arrêt sur
    le port de `<Server port=… shutdown=…>`, comme `shutdown.bat`, et le démarrage lance
    `bin\catalina.bat jpda start` du Tomcat du `server.xml`, dans sa propre console, en mode débogage sur le
    port 8000 (le JDK et la mémoire se règlent dans `bin\setenv.bat`). Pour déboguer : *Run > Debug
    Configurations > Remote Java Application*, host `localhost`, port `8000`.
- Une seconde carte, dessous, regroupe le champ de recherche (il filtre la liste sur le nom de l'application)
  et le tableau. Sous le tableau, un statut indique le nombre de Context actifs (« 3 actifs sur 13 »),
  et le nombre affiché quand la recherche filtre la liste.
- Chaque ligne affiche un point **vert** (actif) ou **rouge** (inactif) et le nom de l'application
  (le `path` sans le `/`, ou ROOT). Cliquer sur le point vert commente le Context entier dans un seul
  bloc `<!-- … -->` ; cliquer sur le point rouge le décommente (y compris un bloc sur plusieurs lignes).
- La liste est triée : les Context actifs d'abord, puis par ordre alphabétique. Activer ou désactiver un
  Context ne change pas sa place (pas de nouveau tri) ; le tri est refait au chargement, après un ajout,
  une duplication, une modification ou une suppression, et quand `server.xml` change sur le disque.
- La fenêtre a une taille fixe, qui montre 10 lignes : au-delà, la liste a son ascenseur ; en dessous,
  les lignes restantes sont simplement vides.
- En haut de la seconde carte, quatre boutons en icônes (sans texte, avec infobulle) agissent sur les Context :
  - **+ Ajouter** ouvre exactement la même fenêtre que Modifier : path, reloadable, docBase, workDir, et un
    Loader prérempli comme celui déjà utilisé dans le fichier, sans ses attributs `debug` et
    `useSystemClassLoaderAsParent` (videz son className pour ne pas en mettre).
    docBase et workDir sont préremplis d'après les derniers Context du fichier (par exemple
    `…\netisys-areo\<nom>\src\main\webapp`) et suivent le path que vous tapez, tant que vous ne les avez pas
    modifiés à la main. Le Context est inséré actif après le dernier, avec la même indentation.
  - **Dupliquer** (deux feuilles) copie le Context sélectionné avec ses éléments enfants (Loader, Resource…).
    La fenêtre est préremplie, avec le path suffixé `_copie`. La copie est insérée active après le dernier
    Context, même si l'original est commenté. Un path déjà utilisé est refusé.
  - **Modifier** (crayon, ou un **double-clic** sur une ligne) ouvre la fenêtre de modification des attributs
    du Context (path, docBase, reloadable, workDir…) puis de son élément `<Loader>`. Les valeurs true/false
    sont des boutons, sur la ligne du champ qui les précède. Cela marche aussi pour un Context inactif.
  - **Supprimer** (corbeille) retire le Context sélectionné de `server.xml`, actif ou commenté, après une
    confirmation qui indique le chemin complet du `server.xml` modifié.
- Il n'y a pas de barre de message : les erreurs s'affichent dans une boîte de dialogue.
- Seule la zone du Context change : indentation, autres commentaires, fins de ligne et encodage
  du fichier sont conservés.
- À la première modification, `server.xml.orig` est créé (copie du fichier d'origine, jamais écrasée).
- L'application refuse d'écrire si le résultat n'est plus du XML valide, et recharge la liste
  si le fichier a été modifié à la main entre-temps.
- Les Context commentés ligne par ligne (une balise `<!-- <Context …> -->`, puis chaque ligne
  commentée, puis `<!-- </Context> -->`, comme le fait Eclipse) sont reconnus et décommentés ligne
  par ligne.
- Un Context qui contient lui-même un commentaire (`<!-- -->`) ne peut pas être commenté
  automatiquement (XML interdit les commentaires imbriqués) : un message l'indique.
- Pensez à redémarrer Tomcat pour que la modification soit prise en compte.
- Si Tomcat est installé dans un dossier protégé (ex. `Program Files`), lancez l'application
  en administrateur pour pouvoir écrire le fichier.

## Prérequis (Windows)

- JDK 25 (par exemple Eclipse Temurin 25), avec `JAVA_HOME` défini
- Maven 3.8 ou plus récent

JavaFX est téléchargé automatiquement par Maven (version Windows).

## Lancer depuis les sources

```bat
cd tomcat-chooser
mvn javafx:run
```

## Télécharger l'application

Chaque version publiée se trouve dans les **Releases** du dépôt, avec un paquet par système.
Java est embarqué dans chaque paquet : aucune installation n'est nécessaire.

- **Windows** : `TomcatChooser-windows.zip`. Dézippez-le et lancez `TomcatChooser\TomcatChooser.exe`.
- **Mac** (Apple Silicon, M1 et suivants) : `TomcatChooser-mac.zip`. Dézippez-le et placez
  `TomcatChooser.app` dans Applications. L'application n'est pas signée : au premier lancement,
  faites clic droit > **Ouvrir** puis confirmez (ou `xattr -cr /Applications/TomcatChooser.app`).
- **Linux** (x64) : `TomcatChooser-linux.tar.gz`. Décompressez-le
  (`tar xzf TomcatChooser-linux.tar.gz`) et lancez `TomcatChooser/bin/TomcatChooser`.

Au premier lancement, le fichier proposé est `C:\Tomcat70\conf\server.xml` sous Windows, et
`$CATALINA_HOME/conf/server.xml` (ou `/opt/tomcat/conf/server.xml`) sur Mac et Linux.

### Publier une nouvelle version

Créez un tag de version commençant par `v` (par exemple `v1.0.0`) et poussez-le :
GitHub construit l'application sous Windows, Mac et Linux puis crée la release avec les trois paquets.

- **Depuis GitHub** : page du dépôt > **Releases** > **Draft a new release** > *Choose a tag*,
  tapez `v1.0.0` > *Create new tag* > **Publish release**. Les paquets sont ajoutés à la release
  quelques minutes plus tard.
- **Depuis Eclipse** : clic droit sur le projet > Team > **Advanced > Tag…**,
  nom `v1.0.0`, message, OK ; puis Team > **Push Tags…** vers `origin`.

Le numéro du tag devient la version de l'application. On peut aussi lancer le build à la main
(onglet **Actions** > « Release » > **Run workflow**) : les paquets sont alors disponibles comme
artefacts de l'exécution, sans release.

## Tester sur Mac ou Linux sans paquet

Avec un JDK 25 et Maven installés : `mvn javafx:run` dans le dossier du projet.

## Construire l'exe Windows soi-même (depuis Eclipse)

1. Clic droit sur le projet > Run As > **Maven build…**
2. Goals : `clean package javafx:jlink exec:exec@jpackage`
3. Onglet **JRE** : choisissez un **JDK 25** (pas un simple JRE : `jpackage` est fourni avec le JDK).
4. Run.

Résultat : `target\dist\TomcatChooser\TomcatChooser.exe`, avec l'icône Tomcat et son propre Java.
Copiez tout le dossier `target\dist\TomcatChooser` sur un autre poste pour l'utiliser : il n'y a
pas besoin d'y installer Java. Pour un installeur `.msi`, il faudrait en plus WiX Toolset.

On peut aussi passer un fichier en argument : `TomcatChooser.exe D:\autre\conf\server.xml`.

## Tests

```bat
mvn test
```

Les tests couvrent la détection des Context (actifs, commentés sur une ou plusieurs lignes),
l'aller-retour commenter/décommenter à l'identique, les sauvegardes, l'encodage ISO-8859-1
et la détection d'une modification externe.

## Structure

```
src/main/java/module-info.java
src/main/java/fr/tomcatchooser/TomcatChooserApp.java   fenêtre JavaFX
src/main/java/fr/tomcatchooser/ServerXml.java          lecture / modification de server.xml
src/main/java/fr/tomcatchooser/ContextEntry.java       un Context trouvé
src/main/resources/fr/tomcatchooser/tomcat-*.png       icône de la fenêtre (logo Apache Tomcat)
src/test/java/fr/tomcatchooser/ServerXmlTest.java      tests JUnit 5
packaging/tomcat.ico                                   icône de l'exécutable (jpackage)
```

Le logo Apache Tomcat est une marque de l'Apache Software Foundation, repris du dépôt
[apache/tomcat](https://github.com/apache/tomcat) (licence Apache 2.0).
