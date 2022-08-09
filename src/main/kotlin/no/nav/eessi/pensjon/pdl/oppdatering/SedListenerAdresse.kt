package no.nav.eessi.pensjon.pdl.oppdatering

import no.nav.eessi.pensjon.eux.EuxDokumentHelper
import no.nav.eessi.pensjon.eux.EuxKlient
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
    private val personidentifiseringService: PersonidentifiseringService,
    private val dokumentHelper: EuxDokumentHelper,
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

                try {
                    val sedHendelse = SedHendelseModel.fromJson(hendelse)
                    logger.info("Ser om sedHendelse allerede ligger i pdl med riktig adresse, rinaId: ${sedHendelse.rinaSakId}, bucType:${sedHendelse.bucType}, sedType:${sedHendelse.sedType}")

                    if (GyldigeHendelser.mottatt(sedHendelse)) {
                        val bucType = sedHendelse.bucType!!
                        val alleGyldigeSED = dokumentHelper.alleGyldigeSEDForBuc(sedHendelse.rinaSakId, dokumentHelper.hentBuc(sedHendelse.rinaSakId))
                        val identifisertePersoner = personidentifiseringService.hentIdentifisertePersoner(alleGyldigeSED, bucType)
                        logger.info("Vi har funnet ${identifisertePersoner.size} personer fra PDL som har gyldige identer")

//                        personidentifiseringService.finnesPersonMedAdressebeskyttelse()

                        val adresseUtland = alleGyldigeSED.firstOrNull()?.second?.nav?.bruker?.adresse
                        val alleAdresser = alleGyldigeSED.filter { it.second.nav?.bruker?.adresse != null }
                            .map { it.second.nav?.bruker?.adresse }
                            .distinct()

                        val adresserIkkeNorske = alleAdresser.filter { it?.land != "NO" }.firstOrNull()

                        if(adresserIkkeNorske != null) {
                            val listMedAdresserFraPdl = identifisertePersoner.map { it.kontaktAdresse.utenlandskAdresse }
                            pdlFiltrering.finnesUtlAdresseFraSedIPDL(listMedAdresserFraPdl, adresserIkkeNorske)
                        }

                        //TODO: send melding til personMottakKlient
                    }

                    //TODO: kall til service for innhenting av opplysninger fra PDL

                    latch.countDown()
                }catch (ex: Exception){
                    logger.error("Noe gikk galt under behandling av SED-hendelse for adresse:\n $hendelse \n", ex)
                }
                //TODO: ack på alt mens det er under utvikling
                acknowledgment.acknowledge()
            }
        }
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