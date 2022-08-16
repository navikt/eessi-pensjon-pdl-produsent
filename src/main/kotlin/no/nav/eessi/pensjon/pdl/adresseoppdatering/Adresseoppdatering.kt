package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.EndringsmeldingUtAdresse
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.filtrering.PdlFiltrering
import no.nav.eessi.pensjon.pdl.validering.erRelevantForEESSIPensjon
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kontaktadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class Adresseoppdatering(
    private val pdlService: PersonidentifiseringService,
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

        val sederFraBuc = euxService.alleGyldigeSEDForBuc(sedHendelse.rinaSakId) // TODO Hvorfor henter vi flere SED'er?

        val adresserUtland = brukersAdresserUtenforNorge(sederFraBuc.map { it.second })
        if (adresserUtland.isEmpty()) {
            return false
        }
        if (adresserUtland.size > 1) {
            logger.warn("Vi fant flere enn én utenlandsk adresse, avslutter")
            return false
        }
        val adresseUtlandFraSed = adresserUtland.first()!!

        // TODO La oss finne aktuelle personer før vi slår dem opp i PDL
        val personerHentetFraPDL = pdlService.hentIdentifisertePersoner(sederFraBuc, sedHendelse.bucType!!)

        logger.info("Vi har funnet ${personerHentetFraPDL.size} personer fra PDL som har gyldige identer")
        val person = personerHentetFraPDL.first()

        if (person.kontaktAdresse?.utenlandskAdresse != null &&
            pdlFiltrering.finnesUtlAdresseFraSedIPDL(person.kontaktAdresse.utenlandskAdresse!!, adresseUtlandFraSed)) {
            logger.info("Adresse finnes allerede i PDL, oppdaterer gyldig til og fra dato")
            lagUtAdresseEndringsMelding(person.kontaktAdresse, person.fnr.toString(), Endringstype.KORRIGER)
            return true
        } else {
            logger.info("Adresse ikke funnet i PDL, kandidat for (fremtidig) oppdatering")
            return false
            //logger.info("Adresse finnes ikke i PDL, oppdaterer kontaktadresse")
            //TODO: send melding for opprettelse til personMottakKlient
        }
    }

    private fun brukersAdresserUtenforNorge(sedList: List<SED>) =
        sedList
            .filter { it.nav?.bruker?.adresse != null }
            .map { it.nav?.bruker?.adresse }
            .filter { it?.land != "NO" }
            .distinct()
            .also { logger.info("Vi har funnet ${it.size} utenlandske adresser som skal vurderes") }

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