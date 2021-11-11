package no.nav.eessi.pensjon.pdl.integrajontest

import io.mockk.clearAllMocks
import io.mockk.mockk
import no.nav.eessi.pensjon.buc.EuxDokumentHelper
import no.nav.eessi.pensjon.buc.EuxKlient
import no.nav.eessi.pensjon.klienter.norg2.Norg2Service
import no.nav.eessi.pensjon.listeners.SedMottattListener
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

internal open class MottattHendelseBase {

    protected val euxKlient: EuxKlient = mockk()
    private val dokumentHelper = EuxDokumentHelper(euxKlient)
    protected val norg2Service: Norg2Service = mockk(relaxed = true)

    protected val personService: PersonService = mockk(relaxed = true)
    private val personidentifiseringService = PersonidentifiseringService(personService)

    protected val mottattListener: SedMottattListener = SedMottattListener(
        personidentifiseringService = personidentifiseringService,
        dokumentHelper = dokumentHelper,
        profile = "test"
    )

    @BeforeEach
    fun setup() {
        mottattListener.initMetrics()
        dokumentHelper.initMetrics()
    }

    @AfterEach
    fun after() {
        clearAllMocks()
    }



}