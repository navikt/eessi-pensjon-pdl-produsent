package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_10
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.klienter.norg2.Norg2Service
import no.nav.eessi.pensjon.klienter.norg2.NorgKlientRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OppgaveRoutingService(private val norg2Service: Norg2Service) {

    private val logger = LoggerFactory.getLogger(OppgaveRoutingService::class.java)

    fun route(routingRequest: OppgaveRoutingRequest): Enhet {
        if (routingRequest.aktorId == null) {
            logger.info("AktørID mangler. Bruker enhet ID_OG_FORDELING.")
            return Enhet.ID_OG_FORDELING
        }

        val tildeltEnhet = tildelEnhet(routingRequest)

        logger.info(
            "Router oppgave til $tildeltEnhet (${tildeltEnhet.enhetsNr}), " +
                    "Buc: ${routingRequest.bucType}, " +
                    "Landkode: ${routingRequest.landkode}, " +
                    "Fødselsdato: ${routingRequest.fdato}, " +
                    "Geografisk Tilknytning: ${routingRequest.geografiskTilknytning}, " +
                    "saktype: ${routingRequest.saktype}"
        )

        return tildeltEnhet
    }

    private fun tildelEnhet(routingRequest: OppgaveRoutingRequest): Enhet {
        val enhet = EnhetFactory.hentHandlerFor(routingRequest.bucType).finnEnhet(routingRequest)

        logger.debug("enhet: $enhet")

        if (routingRequest.bucType == P_BUC_01) {
            val norgKlientRequest = NorgKlientRequest(
                routingRequest.harAdressebeskyttelse,
                routingRequest.landkode,
                routingRequest.geografiskTilknytning,
                routingRequest.saktype
            )
            return norg2Service.hentArbeidsfordelingEnhet(norgKlientRequest) ?: enhet
        }

        if (erGyldigPBuc10(routingRequest.bucType, routingRequest.landkode, routingRequest.sakInformasjon)) {
            logger.debug("Benytter norg2 for buctype: ${routingRequest.bucType}")
            val personRelasjon = routingRequest.identifisertPerson?.personRelasjon
            val norgKlientRequest = NorgKlientRequest(
                routingRequest.harAdressebeskyttelse,
                routingRequest.landkode,
                routingRequest.geografiskTilknytning,
                routingRequest.saktype,
                personRelasjon
            )
            return norg2Service.hentArbeidsfordelingEnhet(norgKlientRequest) ?: enhet
        }
        return enhet
    }

    fun erGyldigPBuc10(bucType: BucType, landkode: String?, sakInformasjon: SakInformasjon?) =
        bucType == P_BUC_10 && landkode == "NOR" && sakInformasjon?.sakType == SakType.ALDER && sakInformasjon.sakStatus == SakStatus.LOPENDE.also { logger.info("sakstatus: ${sakInformasjon.sakStatus}") }
}
