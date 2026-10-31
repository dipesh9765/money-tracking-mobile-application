package com.expenseinterceptor.data.sms

import android.content.Context
import android.provider.Telephony
import com.expenseinterceptor.domain.model.DateRange
import com.expenseinterceptor.domain.port.RawSms
import com.expenseinterceptor.domain.port.SmsInbox
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the system SMS inbox through the content provider. Requires READ_SMS. */
class ContentResolverSmsInbox
@Inject
constructor(@ApplicationContext private val context: Context) : SmsInbox {

  override suspend fun read(range: DateRange): List<RawSms> =
    withContext(Dispatchers.IO) {
      val messages = mutableListOf<RawSms>()
      val cursor =
        context.contentResolver.query(
          Telephony.Sms.Inbox.CONTENT_URI,
          arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.BODY),
          "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?",
          arrayOf(range.startMillis.toString(), range.endExclusiveMillis.toString()),
          "${Telephony.Sms.DATE} DESC",
        )
      cursor?.use {
        val address = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        val date = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
        val body = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
        while (it.moveToNext()) {
          val sender = it.getString(address) ?: continue
          val text = it.getString(body) ?: continue
          messages += RawSms(sender, text, it.getLong(date))
        }
      }
      messages
    }
}
