package io.github.octoclonius.libreauto.car.calls

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import android.util.Log
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import io.github.octoclonius.libreauto.projection.calls.CallerNumber

internal fun isUriNumber(number: String): Boolean = number.contains("@") || number.contains("%40")

internal class CallerLookup(context: Context) {

    private companion object {
        const val TAG = "CallerLookup"
    }

    private val resolver = context.contentResolver
    private val resources = context.resources
    private val phoneNumbers by lazy { PhoneNumberUtil.getInstance() }
    private val geocoder by lazy { PhoneNumberOfflineGeocoder.getInstance() }

    fun numberLabel(number: CallerNumber): String? {
        val uri =
            PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI.buildUpon()
                .appendPath(number.number)
                .appendQueryParameter(
                    PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS,
                    isUriNumber(number.number).toString(),
                )
                .build()
        return try {
            resolver
                .query(uri, arrayOf(PhoneLookup.TYPE, PhoneLookup.LABEL), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return null
                    Phone.getTypeLabel(resources, cursor.getInt(0), cursor.getString(1)).toString()
                }
        } catch (e: SecurityException) {
            Log.i(TAG, "LibreAuto may not read the caller's number label", e)
            null
        }
    }

    fun location(number: CallerNumber): String? {
        val parsed =
            try {
                phoneNumbers.parse(number.number, number.countryIso)
            } catch (e: NumberParseException) {
                Log.i(TAG, "The caller's number has no location: ${e.errorType}")
                return null
            }
        return geocoder.getDescriptionForNumber(parsed, resources.configuration.locales[0])
    }
}
