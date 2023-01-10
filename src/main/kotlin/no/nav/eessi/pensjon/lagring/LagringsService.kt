package no.nav.eessi.pensjon.lagring

import no.nav.eessi.pensjon.gcp.GcpStorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LagringsService (private val gcpStorageService: GcpStorageService) {

    private val logger = LoggerFactory.getLogger(LagringsService::class.java)

    fun lagreHendelseMedSakId(rinaSakId: String) {
        val path = hentPathMedSakId(rinaSakId)

        try {
            val jsondata = rinaSakId

            logger.debug("Lagrer hendelse: $path, data: $jsondata")
            gcpStorageService.lagre(path, jsondata)
        } catch (ex: Exception) {
            logger.error("Feiler ved lagring av data: $path")
        }
    }

    fun kanHendelsenOpprettes(rinaSakId: String) = hentHendelse(rinaSakId) == null

    private fun hentHendelse(rinaSakId: String): String? {
        val path = hentPathMedSakId(rinaSakId)
        logger.info("Henter rinaSakId: $rinaSakId from $path")

        return try {
            val rinaSakIdPayload = gcpStorageService.hent(path)

            logger.debug("Henter hendelse fra: $path, data: $rinaSakIdPayload")
            rinaSakIdPayload

        } catch (ex: Exception) {
            logger.info("Feiler ved henting av data : $path")
            null
        }
    }

    fun hentPathMedSakId(rinaSakId: String): String {
        val path = "rinaSakId-$rinaSakId.json"
        logger.info("Hendelsespath: $path")
        return path
    }
}