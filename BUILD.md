# NguyenTo flat build

This archive keeps Java source files, `plugin.yml`, and `config.yml` at the root (flat layout). Source files retain package `vn.nguyento`; Maven compiles from the root and writes the correctly packaged classes into the JAR.


## Build locally
- Java 21
- Maven 3.9+
- Run: `mvn clean package`
- Output: `target/NguyenTo.jar`

GitHub Actions runs the same build and uploads the JAR artifact.
