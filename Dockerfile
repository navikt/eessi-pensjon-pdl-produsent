FROM navikt/java:17-appdynamics

COPY build/libs/eessi-pensjon-pdl-produsent.jar /app/app.jar

ENV APPD_ENABLED true
ENV APPD_NAME eessi-pensjon
ENV APPD_TIER pdl-produsent
