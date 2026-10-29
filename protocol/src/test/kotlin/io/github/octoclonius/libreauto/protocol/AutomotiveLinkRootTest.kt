package io.github.octoclonius.libreauto.protocol

import javax.security.auth.x500.X500Principal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AutomotiveLinkRootTest {

    @Test
    fun theRootIsGooglesSelfSignedAutomotiveLinkCertificate() {
        val root = googleAutomotiveLinkRoot()
        val name = X500Principal("O=Google Automotive Link, L=Mountain View, ST=California, C=US")
        assertEquals(name, root.subjectX500Principal)
        assertEquals(name, root.issuerX500Principal)
        root.verify(root.publicKey)
    }
}
