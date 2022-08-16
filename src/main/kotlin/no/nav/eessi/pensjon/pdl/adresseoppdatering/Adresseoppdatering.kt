package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.EndringsmeldingUtAdresse
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.filtrering.PdlFiltrering
import no.nav.eessi.pensjon.pdl.validering.erRelevantForEESSIPensjon
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
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

        val bruker = sed.nav?.bruker

        val brukersAdresseIUtlandetFraSED = bruker?.adresse?.let { if (it.land != "NO") it else null }

        if (brukersAdresseIUtlandetFraSED == null) {
            logger.info("Bruker har ikke utenlandsk adresse i SED")
            return false
        }

        val norskPin = bruker.person?.pin?.firstOrNull { it.land == "NO" }

        if (norskPin == null) {
            // TODO Håndtere brukere med ikke-norske identer
            logger.info("Bruker har ikke norsk pin i SED")
            return false
        }

        val personFraPDL = personService.hentPerson(NorskIdent(norskPin.identifikator!!))

        if (personFraPDL == null) {
            logger.info("Bruker ikke funnet i PDL")
            return false
        }

        logger.info("Vi har funnet en person fra PDL med samme norsk identifikator som bruker i SED")


        if (personFraPDL.kontaktadresse?.utenlandskAdresse != null &&
            pdlFiltrering.finnesUtlAdresseFraSedIPDL(personFraPDL.kontaktadresse!!.utenlandskAdresse!!, brukersAdresseIUtlandetFraSED)
        ) {
            logger.info("Adresse finnes allerede i PDL, oppdaterer gyldig til og fra dato")
            lagUtAdresseEndringsMelding(personFraPDL.kontaktadresse!!, norskPin.identifikator!!, Endringstype.KORRIGER)
            return true
        } else {
            logger.info("Adresse ikke funnet i PDL, kandidat for (fremtidig) oppdatering")
            return false
            //logger.info("Adresse finnes ikke i PDL, oppdaterer kontaktadresse")
            //TODO: send melding for opprettelse til personMottakKlient
        }
    }

    fun lagUtAdresseEndringsMelding(kontaktadresse: Kontaktadresse, norskFnr: String, endringstype: Endringstype)  {
        val pdlEndringsOpplysninger = PdlEndringOpplysning(
            listOf(
                Personopplysninger(
                    endringstype = endringstype,
                    ident = norskFnr,
                    endringsmelding = EndringsmeldingUtAdresse(
                        kilde = "EESSI",
                        gyldigFraOgMed = LocalDate.now(),
                        gyldigTilOgMed = LocalDate.now().plusYears(1),
                        coAdressenavn = kontaktadresse.coAdressenavn,
                        adresse = kontaktadresse.utenlandskAdresse
                    ),
                    opplysningstype = Opplysningstype.KONTAKTADRESSE
                )
            )
        )
        personMottakKlient.opprettPersonopplysning(pdlEndringsOpplysninger)
    }
}