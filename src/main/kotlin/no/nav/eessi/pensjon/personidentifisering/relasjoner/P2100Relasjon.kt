package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.PersonIdentier

class P2100Relasjon(private val sed: SED,
                    private val bucType: BucType,
                    private val rinaDocumentId: String) : GjenlevendeHvisFinnes( sed, bucType,rinaDocumentId) {

    override fun hentRelasjoner(): List<PersonIdentier> = hentRelasjonGjenlevendeFnrHvisFinnes(sed.pensjon?.gjenlevende, bestemSaktype(bucType))

}