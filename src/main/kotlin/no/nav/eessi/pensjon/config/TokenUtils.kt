package no.nav.eessi.pensjon.config

import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import java.util.*


 fun getClaims(tokenValidationContextHolder: TokenValidationContextHolder): JwtTokenClaims {
    val context = tokenValidationContextHolder.getTokenValidationContext()
    if(context.issuers.isEmpty())
        throw RuntimeException("No issuer found in context")

    val validIssuer = context.issuers.filterNot { issuer ->
        val oidcClaims = context.getClaims(issuer)
        oidcClaims.expirationTime.before(Date())
    }.map { it }


    if (validIssuer.isNotEmpty()) {
        val issuer = validIssuer.first()
        return context.getClaims(issuer)
    }
    throw RuntimeException("No valid issuer found in context")

}

 fun getToken(tokenValidationContextHolder: TokenValidationContextHolder): JwtToken? {
    val context = tokenValidationContextHolder.getTokenValidationContext()
    if(context.issuers.isEmpty())
        throw RuntimeException("No issuer found in context")
    val issuer = context.issuers.first()

    return context.getJwtToken(issuer)
}