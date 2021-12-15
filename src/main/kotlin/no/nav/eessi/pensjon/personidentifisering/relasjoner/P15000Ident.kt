package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.P15000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

class P15000Ident() : AbstractIdent() {

    private  val gjenlevende: Gjenlevende = Gjenlevende()
    private val forsikret : Forsikret = Forsikret()

    override fun hentRelasjoner(sed: SED): List<Fodselsnummer?> {
        val sedKravString = sed.nav?.krav?.type
        val saktype = if (sedKravString == null) null else mapKravtypeTilSaktype(sedKravString)

        logger.info("${sed.type.name}, krav: $sedKravString,  saktype: $saktype")

        return if (saktype == Saktype.GJENLEV) {
            logger.debug("legger til gjenlevende: ($saktype)")
            listOf(gjenlevende.hentRelasjonGjenlevendeFnrHvisFinnes((sed as P15000).p15000Pensjon?.gjenlevende, sed.type))
        } else {
            logger.debug("legger til forsikret: ($saktype)")
            listOf(forsikret.hentForsikretPerson(sed))
        }
    }

    private fun mapKravtypeTilSaktype(krav: String?): Saktype {
        return when (krav) {
            "02" -> Saktype.GJENLEV
            "03" -> Saktype.UFOREP
            else -> Saktype.ALDER
        }
    }
}