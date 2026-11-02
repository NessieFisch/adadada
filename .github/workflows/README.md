# My Client (Fabric, Minecraft 26.2)

Needs: JDK 25, Fabric Loader 0.19.3+, Fabric API 0.152.0+26.2

## One-time: create the Gradle wrapper (no global Gradle needed)
    base=https://raw.githubusercontent.com/gradle/gradle/v9.5.1
    curl -L "$base/gradle/wrapper/gradle-wrapper.jar" -o gradle/wrapper/gradle-wrapper.jar
    curl -L "$base/gradlew" -o gradlew && curl -L "$base/gradlew.bat" -o gradlew.bat && chmod +x gradlew
(or, if you have Gradle installed: gradle wrapper --gradle-version 9.5.1)

## Build / run
    ./gradlew build        -> build/libs/myclient-1.0.0.jar  (put in mods/ with Fabric API)
    ./gradlew runClient    -> dev client

Right Shift = menu (Sky / Cursor / HUD). Config: config/myclient.json
Windows: install JDK 25 yourself:  winget install EclipseAdoptium.Temurin.25.JDK

## No JDK on your PC? Build it online (free)
1. Make a GitHub account, create a new repository, upload all files of this folder (keep the .github folder).
2. Open the "Actions" tab -> "Build mod" -> wait ~5 min -> download the "myclient-mod" artifact.
3. Unzip it, put myclient-1.0.0.jar into your mods folder (with Fabric Loader + Fabric API for 26.2).
