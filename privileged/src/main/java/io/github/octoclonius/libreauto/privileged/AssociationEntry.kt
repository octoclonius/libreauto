package io.github.octoclonius.libreauto.privileged

internal const val ASSOCIATION_ADDRESS = "02:00:00:00:00:01"

internal data class AssociationEntry(val id: Int, val deviceProfile: String?, val address: String?)

internal fun ourAssociationId(associations: List<AssociationEntry>, profile: String): Int? =
    associations
        .filter {
            it.deviceProfile == profile && it.address.equals(ASSOCIATION_ADDRESS, ignoreCase = true)
        }
        .minOfOrNull { it.id }
