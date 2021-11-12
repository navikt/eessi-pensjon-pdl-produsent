package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.PersonIdentier

class P6000Relasjon(private val sed: SED,
                    private val bucType: BucType,
                    val rinaDocumentId: String) : GjenlevendeHvisFinnes(sed,bucType,rinaDocumentId) {

        override fun hentRelasjoner(): List<PersonIdentier> {
            val forsikret = hentForsikretPerson()
            val gjenlevende =  hentRelasjonGjenlevendeFnrHvisFinnes((sed as P6000).p6000Pensjon?.gjenlevende, bestemSaktype(bucType))

            return gjenlevende.ifEmpty { forsikret }

        }

    }