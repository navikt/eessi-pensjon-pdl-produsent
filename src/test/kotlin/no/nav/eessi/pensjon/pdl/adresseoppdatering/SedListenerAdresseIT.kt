package no.nav.eessi.pensjon.pdl.adresseoppdatering

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.models.SedHendelseModel
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.integrationtest.CustomMockServer
import no.nav.eessi.pensjon.pdl.integrationtest.IntegrationBase
import no.nav.eessi.pensjon.pdl.integrationtest.KafkaTestConfig
import no.nav.eessi.pensjon.personoppslag.pdl.model.PersonMock
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.Bostedsadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit


@SpringBootTest( classes = [KafkaTestConfig::class, IntegrationBase.TestConfig::class])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    controlledShutdown = true,
    topics = ["eessi-basis-sedMottatt-v1"]
)
class SedListenerAdresseIT : IntegrationBase() {

    @Autowired
    lateinit var adresseListener: SedListenerAdresse

    @Test
    fun `Gitt en sed hendelse som kommer på riktig topic og group_id så skal den konsumeres av adresseListener`() {
        val mockSedHendelse: SedHendelseModel = mockk(relaxed = true)

        kafkaTemplate.sendDefault(mockSedHendelse.toJson())

        adresseListener.latch.await(5, TimeUnit.SECONDS)
        assertEquals(0, adresseListener.latch.count)
    }
}