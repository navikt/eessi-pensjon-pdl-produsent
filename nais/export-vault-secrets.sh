#!/usr/bin/env bash

echo "Sjekker eessi-pensjon-pdl-produsent srvPassord"
if test -f /var/run/secrets/nais.io/srveessi-pensjon-pdl-produsent/password;
then
  echo "Setter eessi-pensjon-pdl-produsent srvPassord"
    export srvpassword=$(cat /var/run/secrets/nais.io/srveessi-pensjon-pdl-produsent/password)
fi

echo "Sjekker eessi-pensjon-pdl-produsent srvUsername"
if test -f /var/run/secrets/nais.io/srveessi-pensjon-pdl-produsent/username;
then
    echo "Setter eessi-pensjon-pdl-produsent srvUsername"
    export srvusername=$(cat /var/run/secrets/nais.io/srveessi-pensjon-pdl-produsent/username)
fi


# Team namespace Q2
echo "Sjekker srvpassword eessi-pensjon-pdl-produsent q2 i team namespace"
if test -f /var/run/secrets/nais.io/srveessi-pensjon-pdl-produsent-q2/password;
then
  echo "Setter srvpassword eessi-pensjon-pdl-produsent q2 i team namespace"
    export srvpassword=$(cat /var/run/secrets/nais.io/srveessi-pensjon-pdl-produsent-q2/password)
fi

echo "Sjekker srvusername i eessi-pensjon-pdl-produsent q2 i team namespace"
if test -f /var/run/secrets/nais.io/srveessi-pensjon-pdl-produsent-q2/username;
then
    echo "Setter srvusername i eessi-pensjon-pdl-produsent q2 i team namespace"
    export srvusername=$(cat /var/run/secrets/nais.io/srveessi-pensjon-pdl-produsent-q2/username)
fi


# Team namespace Q1
echo "Sjekker srvpassword eessi-pensjon-pdl-produsent q1 i team namespace"
if test -f /var/run/secrets/nais.io/srveessi-pensjon-pdl-produsent-q1/password;
then
  echo "Setter srvpassword eessi-pensjon-pdl-produsent q1 i team namespace"
    export srvpassword=$(cat /var/run/secrets/nais.io/srveessi-pensjon-pdl-produsent-q1/password)
fi

echo "Sjekker srvusername i eessi-pensjon-pdl-produsent q1 i team namespace"
if test -f /var/run/secrets/nais.io/srveessi-pensjon-pdl-produsent-q1/username;
then
    echo "Setter srvusername i eessi-pensjon-pdl-produsent q1 i team namespace"
    export srvusername=$(cat /var/run/secrets/nais.io/srveessi-pensjon-pdl-produsent-q1/username)
fi

echo "Sjekker eessi_pensjon_pdl-produsent_s3_accesskey"
if test -f /var/run/secrets/nais.io/appcredentials/s3_accesskey;
then
  echo "Setter eessi_pensjon_pdl-produsent_s3_accesskey"
    export s3_accesskey=$(cat /var/run/secrets/nais.io/appcredentials/s3_accesskey)
fi

echo "Sjekker eessi_pensjon_pdl-produsent_s3_secretkey"
if test -f /var/run/secrets/nais.io/appcredentials/s3_secretkey;
then
  echo "Setter eessi_pensjon_pdl-produsent_s3_secretkey"
    export s3_secretkey=$(cat /var/run/secrets/nais.io/appcredentials/s3_secretkey)
fi

