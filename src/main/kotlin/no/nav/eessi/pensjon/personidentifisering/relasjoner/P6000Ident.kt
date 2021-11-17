package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.PersonIdenter

class P6000Ident() : AbstractIdent() {

    private  val gjenlevende: Gjenlevende = Gjenlevende()

    override fun hentRelasjoner(sed: SED): List<PersonIdenter> {
            val forsikret = hentForsikretPerson(sed)
            val gjenlevende =  gjenlevende.hentRelasjonGjenlevendeFnrHvisFinnes((sed as P6000).p6000Pensjon?.gjenlevende, sed.type)

            return gjenlevende.ifEmpty { forsikret }

        }

    }