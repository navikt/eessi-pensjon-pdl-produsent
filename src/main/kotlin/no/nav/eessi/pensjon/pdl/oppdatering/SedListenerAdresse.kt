package no.nav.eessi.pensjon.pdl.oppdatering

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.EuxKlient
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.klienter.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.EndringsmeldingUtAdresse
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.models.SedHendelseModel
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.validering.GyldigeHendelser
import no.nav.eessi.pensjon.pdl.validering.PdlFiltrering
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kontaktadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.CountDownLatch
import javax.annotation.PostConstruct

@Service
class SedListenerAdresse (
    private val pdlService: PersonidentifiseringService,
    private val euxService: EuxService,
    private val kodeverkClient: KodeverkClient,
    private val personMottakKlient: PersonMottakKlient,
    private val euxKlient: EuxKlient,
    private val pdlFiltrering: PdlFiltrering,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    val latch: CountDownLatch = CountDownLatch(1)
    private val logger = LoggerFactory.getLogger(SedListenerAdresse::class.java)

    private lateinit var adresseMetric: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        adresseMetric = metricsHelper.init("consumeIncomingSedForAddress")
    }

    @KafkaListener(
        containerFactory = "sedKafkaListenerContainerFactory",
        topics = ["\${kafka.utenlandskAdresse.topic}"],
        groupId = "\${kafka.utenlandskAdresse.groupid}"
    )
    fun consumeSedMottatt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            adresseMetric.measure {
                logger.debug("sed-hendelse for vurdering av adressemelding mot PDL i partisjon: ${cr.partition()}, med offset: ${cr.offset()} ")

                adresserOppdateringMedAck(hendelse, acknowledgment)
                latch.countDown()
            }
        }
    }

    private fun adresserOppdateringMedAck(hendelse: String, acknowledgment: Acknowledgment) {
        try {
            val sedHendelse = SedHendelseModel.fromJson(hendelse)
            logger.info("Ser om sedHendelse allerede ligger i pdl med riktig adresse, rinaId: ${sedHendelse.rinaSakId}, bucType:${sedHendelse.bucType}, sedType:${sedHendelse.sedType}")

            if (GyldigeHendelser.mottatt(sedHendelse)) {

                val adresserUtland = hentAdresserKunUtland(euxService.alleGyldigeSEDForBuc(sedHendelse.rinaSakId))

                logger.info("Vi har funnet ${adresserUtland.size} utenlandske adresser")

                if(adresserUtland.isEmpty()){
                    logger.info("Ingen andresser fra utland, avslutter validering")
                    return
                }

                if(adresserUtland.size  > 1){
                    logger.error("Vi har flere enn en adresse, avslutter")
                    return
                }

                //Vi har adresser fra utland, og de er gyldige; starter validering
                adresserUtland.firstOrNull()?.let { adresse ->
                    val personerHentetFraPDL = pdlService.hentIdentifisertePersoner(
                        euxService.alleGyldigeSEDForBuc(
                            sedHendelse.rinaSakId
                        ), sedHendelse.bucType!!
                    )

                    logger.info("Vi har funnet ${personerHentetFraPDL.size} personer fra PDL som har gyldige identer")

                    pdlFiltrering.finnesUtlAdresseFraSedIPDL(
                        personerHentetFraPDL.map { it.kontaktAdresse?.utenlandskAdresse },
                        adresse
                    )
                }

                //TODO: send melding til personMottakKlient
            }

            //TODO: kall til service for innhenting av opplysninger fra PDL


        } catch (ex: Exception) {
            logger.error("Noe gikk galt under behandling av SED-hendelse for adresse:\n $hendelse \n", ex)
        }
        acknowledgment.acknowledge()

    }

    private fun hentAdresserKunUtland(sedHendelse: List<Pair<ForenkletSED, SED>>): List<Adresse?> {
        val alleAdresser = sedHendelse
            .filter { it.second.nav?.bruker?.adresse != null }
            .map { it.second.nav?.bruker?.adresse }
            .distinct()

        logger.info("Vi har funnet ${alleAdresser.size} adresser som skal vurderes for validering")

        return alleAdresser.filter { it?.land != "NO" }
    }

    fun lagUtAdresseEndringsMelding(kontaktadresse: Kontaktadresse, norskFnr: String)  {
        val pdlEndringsOpplysninger = PdlEndringOpplysning(
            listOf(
                Personopplysninger(
                    endringstype = Endringstype.OPPRETT,
                    ident = norskFnr,
                    endringsmelding = EndringsmeldingUtAdresse(
                        gyldigFraOgMed = kontaktadresse.gyldigFraOgMed?.toLocalDate(),
                        gylidgTilOgMed = kontaktadresse.gyldigTilOgMed?.toLocalDate(),
                        coAdressenavn = kontaktadresse.coAdressenavn,
                        adresse = kontaktadresse.utenlandskAdresse
                    ),
                    opplysningstype = Opplysningstype.UTENLANDSKIDENTIFIKASJONSNUMMER
                )
            )
        )
        personMottakKlient.opprettPersonopplysning(pdlEndringsOpplysninger)
    }
}