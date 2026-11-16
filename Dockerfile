# --- build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so this layer is reused until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# --- run ---
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home app
USER app
WORKDIR /home/app

COPY --from=build /build/target/docuextract-*.jar app.jar
# Created as the app user so the volume mounted here is writable
RUN mkdir -p /home/app/data/uploads

ENV STORAGE_DIR=/home/app/data/uploads
VOLUME /home/app/data
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
