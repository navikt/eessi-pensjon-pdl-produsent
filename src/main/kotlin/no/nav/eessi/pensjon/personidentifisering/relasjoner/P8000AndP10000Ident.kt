package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.PersonIdenter
import no.nav.eessi.pensjon.personidentifisering.Rolle.BARN
import no.nav.eessi.pensjon.personidentifisering.Rolle.ETTERLATTE
import no.nav.eessi.pensjon.personidentifisering.Rolle.FORSORGER
import no.nav.eessi.pensjon.personidentifisering.UtenlandskPin
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

class P8000AndP10000Ident(): AbstractIdent() {

    private val forsikret : Forsikret = Forsikret()

    override fun hentRelasjoner(sed: SED): List<PersonIdenter> {
        val fnrListe = mutableListOf<PersonIdenter>()
        logger.info("Leter etter gyldig ident og relasjon(er) i SedType: ${sed.type}")

        val forsikret = forsikret.hentForsikretPerson(sed)

        fnrListe.addAll(forsikret)
        hentAnnenpersonRelasjon(sed)?.let { fnrListe.add(it) }

        logger.debug("fnrListe: $fnrListe")

        return fnrListe
    }

    //Annenperson søker/barn o.l
    fun hentAnnenpersonRelasjon(sed: SED): PersonIdenter? {
            val annenPerson = sed.nav?.annenperson?.person

            logger.debug("annenPerson: $annenPerson")
            annenPerson?.let { person ->
                val annenPersonPin = Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator)

                val pinItemUtlandList = UtlandMapping().mapUtenlandsPin(person)

                val annenPersonRelasjon = when (person.rolle) {
                    //Rolle BARN, FORSØRGER, ETTERLATTE,  benyttes ikke i noe journalføring hendelse kun hente ut for...?
                    BARN.kode, FORSORGER.kode, ETTERLATTE.kode -> PersonIdenter(
                        annenPersonPin,
                        pinItemUtlandList,
                        sedType = sed.type,
                        )
                    else -> null
                }
                return annenPersonRelasjon
            }
        return null
    }
}