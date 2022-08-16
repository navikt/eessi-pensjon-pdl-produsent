package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.EndringsmeldingUtAdresse
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.models.SedHendelseModel
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.filtrering.PdlFiltrering
import no.nav.eessi.pensjon.pdl.validering.GyldigeHendelser
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

    fun oppdaterUtenlandskKontaktadresse(sedHendelse: SedHendelseModel): Boolean {
        logger.info("Ser om sedHendelse allerede ligger i pdl med riktig adresse, rinaId: ${sedHendelse.rinaSakId}, bucType:${sedHendelse.bucType}, sedType:${sedHendelse.sedType}")

        if (!GyldigeHendelser.mottatt(sedHendelse)) return false

        val adresserUtland = hentAdresserKunUtland(euxService.alleGyldigeSEDForBuc(sedHendelse.rinaSakId))

        logger.info("Vi har funnet ${adresserUtland.size} utenlandske adresser")

        if (adresserUtland.isEmpty()) {
            logger.info("Ingen andresser fra utland, avslutter validering")
            return false
        }

        if (adresserUtland.size > 1) {
            logger.error("Vi har flere enn en adresse, avslutter")
            return false
        }

        //Vi har adresser fra utland, og de er gyldige; starter validering
        adresserUtland.firstOrNull()?.let { adresse ->
            val personerHentetFraPDL = pdlService.hentIdentifisertePersoner(
                euxService.alleGyldigeSEDForBuc(
                    sedHendelse.rinaSakId
                ), sedHendelse.bucType!!
            )

            logger.info("Vi har funnet ${personerHentetFraPDL.size} personer fra PDL som har gyldige identer")
            val person = personerHentetFraPDL.first()

            if (person.kontaktAdresse?.utenlandskAdresse != null &&
                pdlFiltrering.finnesUtlAdresseFraSedIPDL(person.kontaktAdresse.utenlandskAdresse!!, adresse)) {
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
        return false
    }

    private fun hentAdresserKunUtland(sedHendelse: List<Pair<ForenkletSED, SED>>): List<Adresse?> {
        val alleAdresser = sedHendelse
            .filter { it.second.nav?.bruker?.adresse != null }
            .map { it.second.nav?.bruker?.adresse }
            .distinct()

        logger.info("Vi har funnet ${alleAdresser.size} adresser som skal vurderes for validering")

        return alleAdresser.filter { it?.land != "NO" }
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