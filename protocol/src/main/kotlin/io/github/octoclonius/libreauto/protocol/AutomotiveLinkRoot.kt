package io.github.octoclonius.libreauto.protocol

import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

private const val ROOT_RESOURCE = "google_automotive_link_ca.pem"

fun googleAutomotiveLinkRoot(): X509Certificate {
    val stream =
        checkNotNull(TlsServer::class.java.getResourceAsStream(ROOT_RESOURCE)) {
            "No $ROOT_RESOURCE"
        }
    return stream.use {
        CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
    }
}
