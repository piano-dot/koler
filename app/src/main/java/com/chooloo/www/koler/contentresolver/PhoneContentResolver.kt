package com.chooloo.www.koler.contentresolver

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.chooloo.www.koler.entity.Contact

class PhoneContentResolver(
        context: Context,
        private val contactId: Long? = null,
) : BaseContentResolver<Array<Contact>>(context) {

    override fun onGetUri(): Uri {
        return Phone.CONTENT_URI
    }

    override fun onGetProjection() = arrayOf(
            Phone.CONTACT_ID,
            Phone.DISPLAY_NAME_PRIMARY,
            Phone.NUMBER,
            Phone.STARRED,
            Phone.PHOTO_URI
    )

    override fun onGetSelection(): String? {
        return contactId?.let { "${Phone.CONTACT_ID} = $contactId" }
    }

    override fun convertCursorToContent(cursor: Cursor?): Array<Contact> = ArrayList<Contact>().apply {
        while (cursor != null && cursor.moveToNext()) cursor.apply {
            add(Contact(
                    contactId = getLong(getColumnIndex(Phone.CONTACT_ID)),
                    name = getString(getColumnIndex(Phone.DISPLAY_NAME_PRIMARY)),
                    number = getString(getColumnIndex(Phone.NUMBER)),
                    photoUri = getString(getColumnIndex(Phone.PHOTO_URI)),
                    starred = "1" == getString(getColumnIndex(Phone.STARRED))
            ))
        }
    }.toTypedArray()
}