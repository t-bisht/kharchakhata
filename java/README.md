# java — KharchaKhata backend services

Gradle multi-project. Shared build logic in `buildSrc/`, versions in
`gradle/libs.versions.toml`. Group id `org.tb.khata`. Java 21.

## Subprojects

- `login_engine` — Google OAuth login, token lifecycle, JWT session issuance. Port 8082.

## Build

```bash
cd java
./gradlew build                              # everything
./gradlew :login_engine:bootJar               # runnable fat JAR
./gradlew format                             # Spotless on all subprojects
```
