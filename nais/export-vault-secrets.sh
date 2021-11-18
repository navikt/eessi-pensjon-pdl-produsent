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