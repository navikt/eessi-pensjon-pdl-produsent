package no.nav.eessi.pensjon.OpprettH070

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.Doedsfall
import no.nav.eessi.pensjon.eux.model.sed.H02x
import no.nav.eessi.pensjon.eux.model.sed.H070
import no.nav.eessi.pensjon.eux.model.sed.HBruker
import no.nav.eessi.pensjon.eux.model.sed.HNav
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.PinLandItem
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.PdlPerson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.person.pdl.leesah.Personhendelse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class OpprettH070  {
    private val logger: Logger = LoggerFactory.getLogger(OpprettH070::class.java)

    fun oppretterH070(personhendelse: Personhendelse, pdlPerson: PdlPerson): H070 {

        val navSed = HNav(
            bruker = HBruker(
                //2.1 Dødsdato
                doedsfall = Doedsfall(personhendelse.doedsfall.doedsdato.simpleFormat()),
                person = Person(
                    //1.1 Personnummer
                    // 1.1.7.1 Personnummer
                    //Under skal fyles ut en bolk for hver pin (norsk og utenlandsk)
                    pin = listOf(
                        PinItem(
                            identifikator = pdlPerson.identer.firstOrNull { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }?.ident,
                            // 1.1.7.2 Land
                            land = "NO",
                        ), PinItem(
                            identifikator = pdlPerson.utenlandskIdentifikasjonsnummer.firstOrNull()?.identifikasjonsnummer,
                            // 1.1.7.2 Land
                            land = pdlPerson.utenlandskIdentifikasjonsnummer.firstOrNull()?.utstederland?.substring(0, 2)
                        )
                    ),
                    //1.1.1 Etternavn
                    etternavn = pdlPerson.navn?.etternavn,
                    //1.1.2 Fornavn
                    fornavn = pdlPerson.navn?.fornavn,
                    //1.1.3 Fødselsdato
                    foedselsdato = pdlPerson.foedselsdato?.foedselsdato,
                    //1.1.4 Kjønn
                    kjoenn = pdlPerson.kjoenn?.kjoenn?.name?.substring(0, 1),
                )
            )
        )

        return H070(
            type = SedType.H070,
            hnav = navSed
        )
    }
    fun LocalDate.simpleFormat(): String = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(this)


}