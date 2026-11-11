# Stage 1: build the WAR from the app/ folder
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY app/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY app/src ./src
RUN mvn -q -B package -DskipTests

# Stage 2: Tomcat 10 with the PostgreSQL driver, same layout as the Linux VM
FROM tomcat:10.1-jre17
ADD https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar /usr/local/tomcat/lib/
RUN chmod 644 /usr/local/tomcat/lib/postgresql-42.7.4.jar && mkdir -p /var/lib/docvault/files
COPY --from=build /src/target/docvault.war /usr/local/tomcat/webapps/docvault.war
COPY deploy/docker/docvault.xml /usr/local/tomcat/conf/Catalina/localhost/docvault.xml
