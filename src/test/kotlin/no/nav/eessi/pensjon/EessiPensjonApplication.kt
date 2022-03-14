package no.nav.eessi.pensjon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Profile
import org.springframework.retry.annotation.EnableRetry

@Profile("integrationtest")
@SpringBootApplication
@EnableRetry
class EessiPensjonApplication

fun main(args: Array<String>) {
	runApplication<EessiPensjonApplication>(*args)
}

