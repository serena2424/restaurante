# Compilación
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
# Las pruebas corren en CI (GitHub Actions); acá solo se arma el jar.
RUN mvn -q clean package -Dmaven.test.skip=true

# Ejecución
FROM eclipse-temurin:21-jre
WORKDIR /app
# mysqldump, para las copias de seguridad
RUN apt-get update && apt-get install -y --no-install-recommends default-mysql-client \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# La zona horaria de la JVM define la hora con que se guardan pedidos y ventas.
ENTRYPOINT ["java","-Duser.timezone=America/Argentina/Buenos_Aires","-jar","/app/app.jar"]
