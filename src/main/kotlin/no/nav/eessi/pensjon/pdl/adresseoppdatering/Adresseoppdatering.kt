package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.models.EndringsmeldingKontaktAdresse
import no.nav.eessi.pensjon.models.EndringsmeldingUtenlandskAdresse
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.filtrering.PdlFiltrering
import no.nav.eessi.pensjon.pdl.validering.erRelevantForEESSIPensjon
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kontaktadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class Adresseoppdatering(
    private val personService: PersonService,
    private val euxService: EuxService,
    private val personMottakKlient: PersonMottakKlient,
    private val pdlFiltrering: PdlFiltrering
) {
    private val logger = LoggerFactory.getLogger(Adresseoppdatering::class.java)

    fun oppdaterUtenlandskKontaktadresse(sedHendelse: SedHendelse): Boolean {
        logger.info("SED mottatt, rinaId: ${sedHendelse.rinaSakId}, bucType:${sedHendelse.bucType}, sedType:${sedHendelse.sedType}")

        if (!erRelevantForEESSIPensjon(sedHendelse)) {
            logger.info("SED ikke relevant for EESSI Pensjon")
            return false
        }

        val sed = euxService.hentSed(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)

        logger.debug("SED: $sed")

        val bruker = sed.nav?.bruker

        val brukersAdresseIUtlandetFraSED = bruker?.adresse?.let { if (it.land != "NO") it else null }

        if (brukersAdresseIUtlandetFraSED == null) {
            logger.info("Bruker har ikke utenlandsk adresse i SED")
            return false
        }

        if (sedHendelse.avsenderNavn == null || sedHendelse.avsenderLand == null) {
            logger.error("Mangler avsenderNavn eller avsenderLand i sedHendelse - avslutter adresseoppdatering: $sedHendelse")
            return false
        }

        if(sedHendelse.avsenderId !in listOf("NO:NAVAT05", "NO:NAVAT07")) { // utelater sjekk av at avsenderland og adresseland er likt for preprod
            if (sedHendelse.avsenderLand != sed.nav?.bruker?.adresse?.land) {
                logger.info("Adressens landkode (${sed.nav?.bruker?.adresse?.land}) er ulik landkode på avsenderland (${sedHendelse.avsenderLand}).")
                return false
            }
        }

        val norskPin = bruker.person?.pin?.firstOrNull { it.land == "NO" }

        if (norskPin == null) {
            // TODO Håndtere brukere med ikke-norske identer
            logger.info("Bruker har ikke norsk pin i SED")
            return false
        }

        val personFraPDL = personService.hentPerson(NorskIdent(norskPin.identifikator!!))

        logger.debug("Person fra PDL: $personFraPDL")

        if (personFraPDL == null) {
            logger.info("Bruker ikke funnet i PDL")
            return false
        }

        logger.info("Vi har funnet en person fra PDL med samme norsk identifikator som bruker i SED")

        if (personFraPDL.kontaktadresse?.utenlandskAdresse != null &&
            pdlFiltrering.finnesUtlAdresseFraSedIPDL(personFraPDL.kontaktadresse!!.utenlandskAdresse!!, brukersAdresseIUtlandetFraSED)
        ) {
            logger.info("Adresse finnes allerede i PDL, oppdaterer gyldig til og fra dato")
            lagUtenlandskKontaktAdresseEndringsMelding(
                kontaktadresse = personFraPDL.kontaktadresse!!,
                norskFnr = norskPin.identifikator!!,
                endringstype = Endringstype.KORRIGER,
                kilde = sedHendelse.avsenderNavn + " (" + sedHendelse.avsenderLand + ")"
            )
            return true
        } else {
            logger.info("Adresse ikke funnet i PDL, kandidat for (fremtidig) oppdatering")
            return false
            //TODO: send melding for opprettelse til personMottakKlient
        }
    }

    fun lagUtenlandskKontaktAdresseEndringsMelding(kontaktadresse: Kontaktadresse, norskFnr: String, endringstype: Endringstype, kilde: String) {
        val pdlEndringsOpplysninger = PdlEndringOpplysning(
            listOf(
                Personopplysninger(
                    endringstype = endringstype,
                    ident = norskFnr,
                    endringsmelding = EndringsmeldingKontaktAdresse(
                        kilde = kilde,
                        gyldigFraOgMed = LocalDate.now(),
                        gyldigTilOgMed = LocalDate.now().plusYears(1),
                        coAdressenavn = kontaktadresse.coAdressenavn,
                        adresse = EndringsmeldingUtenlandskAdresse(
                            adressenavnNummer = kontaktadresse.utenlandskAdresse!!.adressenavnNummer,
                            bygningEtasjeLeilighet = kontaktadresse.utenlandskAdresse!!.bygningEtasjeLeilighet,
                            bySted = kontaktadresse.utenlandskAdresse!!.bySted,
                            landkode = kontaktadresse.utenlandskAdresse!!.landkode,
                            postboksNummerNavn = kontaktadresse.utenlandskAdresse!!.postboksNummerNavn,
                            postkode = kontaktadresse.utenlandskAdresse!!.postkode,
                            regionDistriktOmraade = kontaktadresse.utenlandskAdresse!!.regionDistriktOmraade
                        )
                    ),
                    opplysningstype = Opplysningstype.KONTAKTADRESSE,
                    opplysningsId = kontaktadresse.metadata.opplysningsId
                )
            )
        )
        personMottakKlient.opprettPersonopplysning(pdlEndringsOpplysninger)
    }

}