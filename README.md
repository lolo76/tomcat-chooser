# Tomcat Chooser

Petite application Windows (JavaFX, Java 25) qui liste les `<Context>` d'un `server.xml`
Tomcat et permet de les **commenter / décommenter d'un clic**.

![Capture](docs/capture.png)

## Fonctionnement

- Au démarrage, le fichier ouvert est `C:\Tomcat70\conf\server.xml`
  (ou le dernier fichier choisi, mémorisé automatiquement).
- À gauche du chemin, trois icônes bleues : le dossier choisit un autre `server.xml` (on peut aussi taper
  le chemin puis Entrée), ↻ recharge le fichier, et le crayon ouvre `server.xml` dans l'éditeur
  du système (Bloc-notes à défaut) ; rechargez après l'avoir enregistré.
- Chaque ligne affiche le nom de l'application (le `path` sans le `/`, ou ROOT) et un bouton d'état : **Actif** (vert) ou **Inactif** (gris).
- Cliquer sur **Actif** commente le Context entier dans un seul bloc `<!-- … -->` ;
  cliquer sur **Inactif** le décommente (y compris un bloc `<!-- … -->` sur plusieurs lignes).
- La fenêtre s'ajuste pour afficher toutes les lignes, sans dépasser la taille de l'écran.
- Le champ de recherche filtre la liste sur le nom de l'application.
- En bas à droite, un voyant indique si **Tomcat est démarré ou arrêté** (vérifié toutes les 3 s).
  Il teste sur localhost le port HTTP du `server.xml` choisi (jamais le port d'arrêt),
  quel que soit le lanceur (Eclipse, startup.bat, service).
- Un **double-clic** sur une ligne ouvre une fenêtre pour modifier les attributs du Context
  (path, docBase, reloadable, workDir…) et ceux de son élément `<Loader>` :
  modifier une valeur ou supprimer (✕) un attribut. Les valeurs true/false sont des boutons.
  Cela fonctionne aussi pour un Context inactif (commenté).
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

## Télécharger l'exe Windows

Chaque version publiée se trouve dans les **Releases** du dépôt : téléchargez
`TomcatChooser-windows.zip`, dézippez-le et lancez `TomcatChooser\TomcatChooser.exe`
(son Java est embarqué, aucune installation n'est nécessaire).

### Publier une nouvelle version

Créez un tag de version commençant par `v` (par exemple `v1.0.0`) et poussez-le :
GitHub construit l'exe sous Windows puis crée la release avec le zip en pièce jointe.

- **Depuis GitHub** : page du dépôt > **Releases** > **Draft a new release** > *Choose a tag*,
  tapez `v1.0.0` > *Create new tag* > **Publish release**. Le zip est ajouté à la release
  quelques minutes plus tard.
- **Depuis Eclipse** : clic droit sur le projet > Team > **Advanced > Tag…** (ou *Create Tag…*),
  nom `v1.0.0`, message, OK ; puis Team > **Push Tags…** vers `origin`.

Le numéro du tag devient la version de l'exe. On peut aussi lancer le build à la main
(onglet **Actions** > « Exe Windows » > **Run workflow**) : l'exe est alors disponible comme
artefact **TomcatChooser-windows** de l'exécution, sans release.

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
