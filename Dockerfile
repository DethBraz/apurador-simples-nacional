# Build em multi-stage: o JDK completo fica so na etapa de compilacao, e a
# imagem final carrega apenas o JRE. Resultado bem menor e com menos superficie
# de ataque que empacotar o Maven junto.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# pom primeiro: enquanto ele nao muda, o Docker reaproveita a camada de
# dependencias e o build fica muito mais rapido.
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
