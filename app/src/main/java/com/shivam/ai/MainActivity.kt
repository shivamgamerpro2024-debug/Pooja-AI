package com.shivam.ai

import android.Manifest
import android.app.Activity
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {

    private var tts: TextToSpeech? = null
    private lateinit var logView: TextView
    private val REQ_VOICE = 100
    private val REQ_PERM = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(20))
        }
        val title = TextView(this).apply {
            text = "SHIVAM AI"
            textSize = 30f
            gravity = Gravity.CENTER
        }
        val talk = Button(this).apply {
            text = "🎤  बोलिए"
            textSize = 22f
            setOnClickListener { listen() }
        }
        val notif = Button(this).apply {
            text = "नोटिफिकेशन पढ़ने की अनुमति"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        logView = TextView(this).apply {
            textSize = 16f
            text = "बटन दबाकर बोलिए। जैसे:\n" +
                "• राहुल को कॉल करो\n" +
                "• कॉल उठाओ\n" +
                "• व्हाट्सएप खोलो\n" +
                "• अरिजीत सिंह का गाना चलाओ\n" +
                "• नोटिफिकेशन पढ़ो\n" +
                "• राहुल को मैसेज भेजो कि मैं आ रहा हूँ\n" +
                "• सुबह 6 बजे अलार्म लगाओ\n" +
                "• समय क्या हुआ\n"
        }
        root.addView(title)
        root.addView(talk)
        root.addView(notif)
        root.addView(ScrollView(this).apply { addView(logView) })
        setContentView(root)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale("hi", "IN")
        }

        requestPermissions(
            arrayOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.READ_CONTACTS
            ), REQ_PERM
        )
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun say(msg: String) {
        logView.append("\n➤ $msg")
        tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "shivam")
    }

    // ---------- आवाज़ सुनना ----------
    private fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "बोलिए...")
        }
        try {
            startActivityForResult(i, REQ_VOICE)
        } catch (e: ActivityNotFoundException) {
            say("आवाज़ पहचान उपलब्ध नहीं है। Google ऐप अपडेट कीजिए।")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VOICE && resultCode == RESULT_OK) {
            val said = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: return
            handle(said.trim())
        }
    }

    private fun has(t: String, vararg words: String) = words.any { t.contains(it) }

    // ---------- आदेश समझना ----------
    private fun handle(text: String) {
        logView.append("\n\nआपने कहा: $text")
        val t = text.lowercase(Locale.ROOT)
        when {
            has(t, "उठाओ", "उठा लो", "रिसीव", "answer") && has(t, "कॉल", "फोन", "फ़ोन", "call") -> answerCall()
            has(t, "नोटिफिकेशन", "नोटिफ़िकेशन", "सूचना", "notification") -> readNotifications()
            has(t, "अलार्म", "alarm") -> setAlarm(t)
            has(t, "मैसेज", "संदेश", "message", "sms") -> sendMessage(text)
            has(t, "कॉल", "फोन लगा", "फ़ोन लगा", "डायल", "call") -> makeCall(t)
            has(t, "गाना", "गाने", "म्यूजिक", "म्यूज़िक", "संगीत", "song", "music") -> playMusic(t)
            has(t, "खोलो", "खोल", "ओपन", "open", "चालू") -> openApp(t)
            has(t, "समय", "टाइम", "कितने बजे") -> tellTime()
            else -> say("माफ़ कीजिए, मैं समझ नहीं पाया।")
        }
    }

    // ---------- कॉल करना ----------
    private val callStop = setOf(
        "को", "कॉल", "फोन", "फ़ोन", "लगाओ", "लगा", "करो", "कर", "दो", "डायल",
        "call", "please", "जी", "से", "बात", "मिलाओ", "मिला"
    )

    private fun makeCall(t: String) {
        val name = t.split(" ").filter { it.isNotBlank() && it !in callStop }.joinToString(" ")
        if (name.isEmpty()) { say("किसे कॉल करूँ?"); return }
        val digits = name.filter { it.isDigit() }
        val number = if (digits.length >= 5) digits else lookupNumber(name)
        if (number == null) { say("$name नाम का संपर्क नहीं मिला।"); return }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            say("पहले कॉल की अनुमति दीजिए।"); return
        }
        say("$name को कॉल लगा रहा हूँ।")
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number))))
    }

    private fun lookupNumber(name: String): String? {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val phone = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        contentResolver.query(
            phone,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return null
    }

    // ---------- कॉल उठाना ----------
    private fun answerCall() {
        if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            say("कॉल उठाने की अनुमति नहीं मिली है।"); return
        }
        try {
            val tm = getSystemService(TELECOM_SERVICE) as TelecomManager
            tm.acceptRingingCall()
            say("कॉल उठा ली।")
        } catch (e: SecurityException) {
            say("Android ने कॉल उठाने की अनुमति नहीं दी।")
        }
    }

    // ---------- म्यूज़िक ----------
    private val musicStop = setOf(
        "गाना", "गाने", "चलाओ", "बजाओ", "चला", "बजा", "प्ले", "play", "म्यूजिक", "म्यूज़िक",
        "music", "song", "संगीत", "का", "की", "के", "सुनाओ", "दो", "एक", "कोई"
    )

    private fun playMusic(t: String) {
        val q = t.split(" ").filter { it.isNotBlank() && it !in musicStop }.joinToString(" ")
        try {
            if (q.isEmpty()) {
                val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                say("म्यूज़िक ऐप खोल रहा हूँ।")
            } else {
                val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    putExtra(SearchManager.QUERY, q)
                }
                startActivity(i)
                say("$q चला रहा हूँ।")
            }
        } catch (e: ActivityNotFoundException) {
            val web = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)))
            try { startActivity(web); say("YouTube पर खोज रहा हूँ।") }
            catch (e2: ActivityNotFoundException) { say("कोई म्यूज़िक ऐप नहीं मिला।") }
        }
    }

    // ---------- ऐप खोलना ----------
    private val openStop = setOf("खोलो", "खोल", "दो", "ओपन", "open", "चालू", "करो", "ऐप", "एप", "app", "को", "जी", "चलाओ")

    private val aliases = listOf(
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "वॉट्सऐप" to "whatsapp", "वाट्सएप" to "whatsapp",
        "यूट्यूब" to "youtube", "यूटयूब" to "youtube", "कैमरा" to "camera", "क्रोम" to "chrome",
        "सेटिंग" to "settings", "गैलरी" to "gallery", "इंस्टाग्राम" to "instagram",
        "फेसबुक" to "facebook", "मैप" to "maps", "जीमेल" to "gmail", "कैलकुलेटर" to "calculator",
        "घड़ी" to "clock", "टेलीग्राम" to "telegram", "फोन" to "phone", "स्पॉटिफाई" to "spotify"
    )

    private fun openApp(t: String) {
        var target = t.split(" ").filter { it.isNotBlank() && it !in openStop }.joinToString(" ")
        for ((hi, en) in aliases) if (target.contains(hi)) { target = en; break }
        if (target.isEmpty()) { say("कौन सा ऐप खोलूँ?"); return }

        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = packageManager.queryIntentActivities(launcher, 0).firstOrNull {
            it.loadLabel(packageManager).toString().lowercase(Locale.ROOT).contains(target)
        }
        val intent = match?.let { packageManager.getLaunchIntentForPackage(it.activityInfo.packageName) }
        if (intent == null) { say("$target नाम का ऐप नहीं मिला।"); return }
        say("$target खोल रहा हूँ।")
        startActivity(intent)
    }

    // ---------- नोटिफिकेशन पढ़ना ----------
    private fun readNotifications() {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (enabled == null || !enabled.contains(packageName)) {
            say("पहले 'नोटिफिकेशन पढ़ने की अनुमति' बटन दबाकर SHIVAM AI को चालू कीजिए।")
            return
        }
        val list = NotifService.items.take(5)
        if (list.isEmpty()) { say("कोई नया नोटिफिकेशन नहीं है।"); return }
        say("आपके नोटिफिकेशन: " + list.joinToString(" । "))
    }

    // ---------- मैसेज (SMS तैयार करना) ----------
    private fun sendMessage(text: String) {
        val name = text.substringBefore(" को").trim()
        val body = if (text.contains(" कि ")) text.substringAfter(" कि ").trim() else ""
        val number = lookupNumber(name)
        if (number == null) { say("$name नाम का संपर्क नहीं मिला।"); return }
        say("मैसेज तैयार है। भेजने के लिए Send दबाइए।")
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))
            .putExtra("sms_body", body)
        startActivity(i)
    }

    // ---------- अलार्म ----------
    private val numWords = mapOf(
        "एक" to 1, "दो" to 2, "तीन" to 3, "चार" to 4, "पाँच" to 5, "पांच" to 5,
        "छह" to 6, "छः" to 6, "छे" to 6, "सात" to 7, "आठ" to 8, "नौ" to 9,
        "दस" to 10, "ग्यारह" to 11, "बारह" to 12
    )

    private fun setAlarm(t: String) {
        var hour: Int? = null
        var min = 0

        val m = Regex("(\\d{1,2})(?:[:.](\\d{2}))?").find(t)
        if (m != null) {
            hour = m.groupValues[1].toInt()
            if (m.groupValues[2].isNotEmpty()) min = m.groupValues[2].toInt()
        } else {
            val tokens = t.split(" ").filter { it.isNotBlank() }
            for (i in 0 until tokens.size - 1) {
                if (tokens[i + 1].startsWith("बजे") && numWords.containsKey(tokens[i])) {
                    hour = numWords[tokens[i]]; break
                }
            }
        }
        if (hour == null) { say("कितने बजे का अलार्म लगाऊँ?"); return }

        if (min == 0) {
            if (has(t, "साढ़े", "साढे")) min = 30
            else if (has(t, "सवा")) min = 15
        }
        if (has(t, "शाम", "रात", "दोपहर", "pm") && hour < 12) hour += 12
        if (hour > 23 || min > 59) { say("समय सही नहीं लगा।"); return }

        val i = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, min)
            putExtra(AlarmClock.EXTRA_MESSAGE, "SHIVAM AI")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        try {
            startActivity(i)
            say("$hour बजकर $min मिनट का अलार्म लगा दिया।")
        } catch (e: Exception) {
            say("अलार्म नहीं लग पाया।")
        }
    }

    // ---------- समय ----------
    private fun tellTime() {
        val c = Calendar.getInstance()
        var h = c.get(Calendar.HOUR)
        if (h == 0) h = 12
        say("अभी $h बजकर ${c.get(Calendar.MINUTE)} मिनट हुए हैं।")
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
