package no.nav.eessi.pensjon.pdl.dodsmelding

import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.klienter.saf.BrukerIdType
import no.nav.eessi.pensjon.klienter.saf.SafClient
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.utils.toJson
import no.nav.person.pdl.leesah.Personhendelse
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service

@Service
class MeldingFraPdlListener(
    private val safClient: SafClient,
    private val personService: PersonService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val logger = LoggerFactory.getLogger(MeldingFraPdlListener::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    private val messureOpplysningstype = MessureOpplysningstypeHelper()

    private var leesahKafkaListenerMetric : MetricsHelper.Metric = metricsHelper.init("leesahPersonoppslag")

    init {
        messureOpplysningstype.clearAll()
    }


    @KafkaListener(
        autoStartup = "\${pdl.kafka.autoStartup}",
        batch = "true",
        topics = ["pdl.leesah-v1"],
        groupId = "eessi-pensjon-pdl-produsent",
        containerFactory = "kafkaAivenHendelseListenerAvroLatestContainerFactory",
    )
    fun mottaLeesahMelding(consumerRecords: List<ConsumerRecord<String, Personhendelse>>, ack: Acknowledgment) {
        try {
            logger.info("Behandler ${consumerRecords.size} meldinger, firstOffset=${consumerRecords.first().offset()}, lastOffset=${consumerRecords.last().offset()}")
            consumerRecords.forEach { record ->
                leesahKafkaListenerMetric.measure {
                    val personhendelse = record.value()
                    logger.info("Undersøker type: ${personhendelse.opplysningstype}")

                    when (personhendelse.opplysningstype) {
                        "DOEDSFALL_V1" -> {
                            logger.debug("DOEDSFALL_V1: ${personhendelse}")
                            secureLogger.info("DOEDSFALL_V1: ${personhendelse}")
                            messureOpplysningstype.addKjent(personhendelse)

                            val valgtPersonident = hentAlleNorskeIdenter(personhendelse)

                            if (valgtPersonident == null) {
                                logger.warn("Fant ingen gyldig ident i personidenter: ${personhendelse.personidenter}")
                            } else {
                                logger.info("Henter informasjon for ident: ${valgtPersonident.take(4)}")
                                val identFraPdl = Ident.bestemIdent(valgtPersonident)

                                val person = personService.hentPerson(identFraPdl).also { pdlPerson ->
                                    logger.debug("Henter person: {}", pdlPerson)
                                }

                                val gyldigeUtstederland = listOf("SWE", "FIN", "POL")
                                val landFraIdentUtland = person?.utenlandskIdentifikasjonsnummer
                                    ?.map { it.utstederland }
                                    ?.toSet().also { logger.debug("Henter land: $it") }

                                if (!landFraIdentUtland.isNullOrEmpty()) {
                                    if (landFraIdentUtland.any { it in gyldigeUtstederland }) {
                                        logger.info("Har utenlandskIdentifikasjonsnummer, henter dokumentmetadata fra saf")
                                        val responseFraSaf = safClient.hentDokumentMetadata(valgtPersonident, BrukerIdType.FNR)
                                        logger.info("Svar fra saf: $responseFraSaf")
                                    } else {
                                        logger.info("${landFraIdentUtland.toJson()} er ikke inkludert i listen: $gyldigeUtstederland, henter ikke dokumentmetadata fra saf")
                                    }
                                } else {
                                    logger.info("Ingen utenlandskIdentifikasjonsnummer funnet, henter ikke dokumentmetadata fra saf")
                                }
                            }
                        }
                        "BOSTEDSADRESSE_V1", "KONTAKTADRESSE_V1", "OPPHOLDSADRESSE_V1" -> {
                            logger.debug("ADRESSE_V1: ${personhendelse}")
                            messureOpplysningstype.addKjent(personhendelse)
                        }
                        else -> {
                            logger.debug("Behandler ikke ${personhendelse.opplysningstype}, ignorerer melding")
                            messureOpplysningstype.addUkjent(personhendelse)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Behandling av hendelse feilet", e)
            throw e
        }
//        ack.acknowledge()
        messureOpplysningstype.createMetrics()
        messureOpplysningstype.clearAll()
        logger.info("Acket personhendelse")
    }

    private fun hentAlleNorskeIdenter(personhendelse: Personhendelse?): String? {
        val valgtPersonident = personhendelse?.personidenter
            ?.firstOrNull { ident ->
                try {
                    Ident.bestemIdent(ident)
                    true
                } catch (e: Exception) {
                    logger.debug("Ignorerer ident som ikke kan bestemmes: $ident", e)
                    false
                }
            }
        return valgtPersonident
    }

    class MessureOpplysningstypeHelper() {

        private val logger: Logger = LoggerFactory.getLogger(javaClass)
        private val knownType : MutableList<String> = mutableListOf()
        private val unkownType : MutableList<String> = mutableListOf()

        fun addKjent(personhendelse: Personhendelse) = knownType.add(personhendelse.opplysningstype)

        fun addUkjent(personhendelse: Personhendelse) = unkownType.add(personhendelse.opplysningstype)

        fun createMetrics() {
            try {
                knownType.map { navn ->
                    logger.debug("Opplysningstype: $navn")
                    Metrics.counter("personhendelse_kjent_opplysningstype", "Navn", navn).increment()
                }
                unkownType.map { navn ->
                    logger.debug("Ukjentopplysningstype: $navn")
                    Metrics.counter("personhendelse_ukjent_opplysningstype", "Navn", navn).increment()
                }
            } catch (_: Exception) {
                logger.warn("Metrics feilet på opplysningstype")
            }
        }

        fun clearAll() {
            knownType.clear()
            unkownType.clear()
            logger.info("messureOpplysningstype all cleared")
        }

    }
}