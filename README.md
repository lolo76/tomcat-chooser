# Tomcat Chooser

Petite application Windows (JavaFX, Java 21) qui liste les `<Context>` d'un `server.xml`
Tomcat et permet de les **commenter / décommenter d'un clic**.

![Capture](docs/capture.png)

## Fonctionnement

- Au démarrage, le fichier ouvert est `C:\Tomcat70\conf\server.xml`
  (ou le dernier fichier choisi, mémorisé automatiquement).
- **Parcourir…** permet de choisir un autre `server.xml` ; on peut aussi taper le chemin puis Entrée.
- Chaque ligne affiche le nom de l'application (le `path` sans le `/`, ou ROOT) et un bouton d'état : **Actif** (vert) ou **Inactif** (gris).
- Cliquer sur **Actif** commente le Context entier dans un seul bloc `<!-- … -->` ;
  cliquer sur **Inactif** le décommente (y compris un bloc `<!-- … -->` sur plusieurs lignes).
- La fenêtre s'ajuste pour afficher toutes les lignes, sans dépasser la taille de l'écran.
- Seule la zone du Context change : indentation, autres commentaires, fins de ligne et encodage
  du fichier sont conservés.
- Avant chaque écriture :
  - `server.xml.orig` est créé la première fois (copie du fichier d'origine, jamais écrasée) ;
  - `server.xml.bak` contient l'état juste avant la dernière modification.
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

- JDK 21 (par exemple Eclipse Temurin 21), avec `JAVA_HOME` défini
- Maven 3.8 ou plus récent

JavaFX est téléchargé automatiquement par Maven (version Windows).

## Lancer depuis les sources

```bat
cd tomcat-chooser
mvn javafx:run
```

## Construire une version autonome (sans Java installé sur le poste cible)

```bat
mvn clean package javafx:jlink
```

Résultat :
- `target\tomcat-chooser\bin\tomcat-chooser.bat` : lanceur
- `target\tomcat-chooser-windows.zip` : le même dossier zippé, à copier sur un autre poste

Pour obtenir un vrai `TomcatChooser.exe` :

```bat
jpackage --type app-image --name TomcatChooser ^
  --runtime-image target\tomcat-chooser ^
  --module fr.tomcatchooser/fr.tomcatchooser.TomcatChooserApp ^
  --icon packaging\tomcat.ico ^
  --dest target\dist
```

L'exécutable est alors `target\dist\TomcatChooser\TomcatChooser.exe`
(ajoutez `--type msi` à la place de `app-image` pour un installeur, ce qui nécessite WiX Toolset).

On peut aussi passer un fichier en argument : `tomcat-chooser.bat D:\autre\conf\server.xml`.

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
