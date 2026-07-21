# ─── Stage 1: Build ───────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Installa Node.js 18
RUN apt-get update && apt-get install -y \
    curl && \
    curl -fsSL https://deb.nodesource.com/setup_18.x \
    | bash - && \
    apt-get install -y nodejs && \
    apt-get clean

# Copia tutto il progetto
COPY . .

# Build Maven con profilo prod
# (include build frontend React)
RUN mvn -Pprod clean package -DskipTests

# ─── Stage 2: Runtime ─────────────────────
FROM tomcat:10.1-jdk17

# Rimuovi app di default Tomcat
RUN rm -rf /usr/local/tomcat/webapps/*

# Crea struttura app in webapps
RUN mkdir -p \
    /usr/local/tomcat/webapps/sostitutoincloud/WEB-INF/classes \
    /usr/local/tomcat/webapps/sostitutoincloud/WEB-INF/lib

# Copia struttura compilata dallo stage build
# (my-build.xml ha già popolato WEB-INF/ 
#  nella cartella del progetto durante mvn package)
COPY --from=builder \
    /build/WEB-INF/ \
    /usr/local/tomcat/webapps/sostitutoincloud/WEB-INF/

# Copia static frontend (config.json incluso)
COPY --from=builder \
    /build/WEB-INF/classes/static/ \
    /usr/local/tomcat/webapps/sostitutoincloud/WEB-INF/classes/static/

# Porta Tomcat
EXPOSE 8080

# Variabili ambiente override a runtime
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms512m -Xmx1024m"

CMD ["catalina.sh", "run"]
