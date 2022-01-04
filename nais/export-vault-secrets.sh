#!/usr/bin/env bash

echo "Sjekker eessi-pensjon-pdl-produsent srvPassord"
if test -f /var/run/secrets/nais.io/srveessipensjonpdlp/password;
then
  echo "Setter eessi-pensjon-pdl-produsent srvPassord"
    export srvpassword=$(cat /var/run/secrets/nais.io/srveessipensjonpdlp/password)
fi

echo "Sjekker eessi-pensjon-pdl-produsent srvUsername"
if test -f /var/run/secrets/nais.io/srveessipensjonpdlp/username;
then
    echo "Setter eessi-pensjon-pdl-produsent srvUsername"
    export srvusername=$(cat /var/run/secrets/nais.io/srveessipensjonpdlp/username)
fi

echo "Sjekker eessi_pensjon_pdlp_s3_creds_password"
if test -f /var/run/secrets/nais.io/appcredentials/eessi_pensjon_pdlp_s3_creds_password;
then
  echo "Setter eessi_pensjon_pdlp_s3_creds_password"
    export eessi_pensjon_pdlp_s3_creds_password=$(cat /var/run/secrets/nais.io/appcredentials/eessi_pensjon_pdlp_s3_creds_password)
fi