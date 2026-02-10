//package no.nav.eessi.pensjon.pdl.dodsmelding
//
//import com.fasterxml.jackson.databind.DeserializationFeature
//import com.fasterxml.jackson.databind.MapperFeature
//import com.fasterxml.jackson.databind.ObjectMapper
//import com.fasterxml.jackson.databind.json.JsonMapper
//import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
//import io.mockk.every
//import io.mockk.justRun
//import io.mockk.mockk
//import io.mockk.verify
//import no.nav.person.pdl.leesah.Personhendelse
//import org.apache.kafka.clients.consumer.ConsumerRecord
//import org.junit.jupiter.api.Test
//import org.springframework.kafka.support.Acknowledgment
//
//class MeldingFraPdlListenerTest {
//
//
//    private val listener = MeldingFraPdlListener()
//    private val mockAck = mockk<Acknowledgment>()
//    private val mapper = configureObjectMapper()
//
//    @Test
//    fun `personalhendelse på sivilstand skal gå ok`() {
//        val hendelse = hentHendelsefraFil("/leesha/leesha_sivilstandhendelse1.json")
//        justRun { mockAck.acknowledge() }
//
//        listener.mottaLeesahMelding(mockConsumerRecord(listOf(hendelse)), mockAck)
//    }
//
//    @Test
//    fun `personhendelse på dødsfall records skal gå ok`() {
//        val hendelse1 = hentHendelsefraFil("/leesha/leesha_doedsfall_hendelse1.json")
//
//        justRun { mockAck.acknowledge() }
//
//        listener.mottaLeesahMelding(mockConsumerRecord(listOf(hendelse1)), mockAck)
//
//        verify(exactly = 1) { mockAck.acknowledge() }
//    }
//
//    private fun mockConsumerRecord(personhendelse: List<Personhendelse>): List<ConsumerRecord<String, Personhendelse>> =
//        personhendelse.map {
//            ConsumerRecord("topic", 0, 1L, it.hendelseId, it)
//        }
//
//    private fun hentHendelsefraFil(hendelseJson: String): Personhendelse =
//        mapper.readValue(javaClass.getResource(hendelseJson).readText(), Personhendelse::class.java)
//
//    private fun hentHendelsefraFil(hendelseJson: String, oldpid: String, newpid: String): Personhendelse =
//        mapper.readValue(javaClass.getResource(hendelseJson).readText().replace(oldpid, newpid), Personhendelse::class.java)
//
//
//    fun configureObjectMapper(): ObjectMapper {
//        return JsonMapper.builder()
//            .addModule(JavaTimeModule())
//            .configure(MapperFeature.USE_ANNOTATIONS, false)
//            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
//            .build()
//    }
//}