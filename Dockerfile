FROM navikt/java:17-appdynamics

COPY build/libs/eessi-pensjon-pdl-produsent.jar /app/app.jar
COPY nais/export-vault-secrets.sh /init-scripts/

ENV APPD_ENABLED true
ENV APPD_NAME eessi-pensjon
ENV APPD_TIER pdl-produsent
