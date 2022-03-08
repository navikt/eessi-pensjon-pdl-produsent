package no.nav.eessi.pensjon.lagring

import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.models.SedHendelseModel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LagringsService (private val s3StorageService: GcpStorageService) {

    private val logger = LoggerFactory.getLogger(LagringsService::class.java)

    fun lagreHendelseMedSakId(hendelse: SedHendelseModel) {
        val path = hentPathMedSakId(hendelse)

        try {
            val jsondata = hendelse.rinaSakId

            logger.debug("Lagrer hendelse: $path, data: $jsondata")
            s3StorageService.lagre(path, jsondata)
        } catch (ex: Exception) {
            logger.error("Feiler ved lagring av data: $path")
        }
    }

    fun kanHendelsenOpprettes(hendelseModel: SedHendelseModel) = hentHendelse(hendelseModel) == null

    fun hentHendelse(hendelse: SedHendelseModel): String? {
        val path = hentPathMedSakId(hendelse)
        logger.info("Henter rinaSakId: ${hendelse.rinaSakId} from $path")

        return try {
            val rinaSakId = s3StorageService.hent(path)

            logger.debug("Henter hendelse fra: $path, data: $rinaSakId")
            rinaSakId

        } catch (ex: Exception) {
            logger.info("Feiler ved henting av data : $path")
            null
        }
    }

    fun hentPathMedSakId(hendelse: SedHendelseModel): String {
        val path = "rinaSakId-${hendelse.rinaSakId}.json"
        logger.info("Hendelsespath: $path")
        return path
    }
}