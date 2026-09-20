package com.example.scamshield

enum class RiskLevel(val label: String) {
    LOW("LOW RISK"),
    MEDIUM("MEDIUM RISK"),
    HIGH("HIGH RISK")
}

// Scam journey-oda 5 stages
enum class ScamStage(val label: String, val bit: Int) {
    OFFER("Fake offer", 1),
    LINK("Suspicious link", 2),
    PAYMENT("Payment request", 4),
    OTP("OTP / PIN request", 8),
    PRESSURE("Pressure / urgency", 16)
}

data class ScamResult(
    val score: Int,
    val level: RiskLevel,
    val reasons: List<String>,
    val stages: Set<ScamStage> = emptySet()
)

object ScamDetector {

    private fun rx(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    // Score-ku risk level tharum. Journey tracker-um idhaye use pannum.
    fun levelFor(score: Int): RiskLevel = when {
        score >= 50 -> RiskLevel.HIGH
        score >= 20 -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
    }

    // Official websites. Idhula irukkura link-a risky-nu solla maatom.
    private val trustedDomains = listOf(
        "amazon.in", "amazon.com", "flipkart.com",
        "sbi.co.in", "onlinesbi.sbi", "hdfcbank.com", "icicibank.com", "axisbank.com",
        "paytm.com", "phonepe.com", "incometax.gov.in", "indiapost.gov.in", "gov.in"
    )

    // Link-la irundhu website peyar mattum edukkum
    private fun hostOf(link: String): String {
        return link.trim().lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore("?")
            .substringBefore("#")
            .substringBefore(":")
            .trimEnd('.', ',', ';', ')')
            .removePrefix("www.")
    }

    // Host full-a match aanaa mattum trusted. amazon.in.fake.xyz trusted illa.
    private fun isTrustedLink(link: String): Boolean {
        val host = hostOf(link)
        return trustedDomains.any { host == it || host.endsWith(".$it") }
    }

    private val linkRegex = rx(
        """https?://\S+|www\.\S+|\b[a-z0-9-]+(\.[a-z0-9-]+)*\.(com|in|net|org|xyz|top|click|link|info|online|site|shop|vip|cc|tk|ml|ga|cf|gq)\b\S*"""
    )

    private val riskyLinkRegex = rx(
        """\b(bit\.ly|tinyurl\.com|cutt\.ly|is\.gd|rb\.gy|t\.co|goo\.gl)\b|\.(xyz|top|click|link|info|online|site|shop|vip|cc|tk|ml|ga|cf|gq)\b|https?://\d{1,3}(\.\d{1,3}){3}|[a-z0-9]+-[a-z0-9]+-[a-z0-9]+\.[a-z]"""
    )

    private val safeShareWarningRegex = rx(
        """(do not|don['’]t|dont|never|not to)\s+(share|tell|give|disclose|reveal)[^.]*"""
    )

    private val otpRequestRegex = rx(
        """\b(share|send|tell|give|provide|forward)\b[^.\n]{0,25}\b(otp|verification code|one[- ]time password)\b"""
    )

    private val pinRegex = rx(
        """\b(share|send|tell|give|provide|enter|update|confirm|reply)\b[^.\n]{0,25}\b(pin|mpin|password|cvv|passcode)\b"""
    )

    private val paymentRegex = rx(
        """\b(pay|send|transfer|deposit)\b[^.\n]{0,25}(?:\b(?:rs|inr|rupees|money|amount|fee|charges)\b|₹)|\b(processing|registration|activation|clearance|customs|refundable)\s+(fee|charge|deposit)|\b(make|complete)\s+(the\s+)?payment\b|\bscan\b[^.\n]{0,15}\bqr\b|\bupi\s*id\b"""
    )

    private val rewardRegex = rx(
        """\b(won|winner|lottery|prize|congratulations|lucky draw|free gift|cashback|jackpot|claim your|you are selected|selected for)\b"""
    )

    private val kycRegex = rx(
        """\b(kyc|pan card|pan number|aadhaar|aadhar)\b|account\s+(will be\s+|has been\s+|is\s+)?(blocked|suspended|frozen|closed|deactivated)|card\s+(will be\s+|has been\s+|is\s+)?(blocked|suspended)|verify your (account|identity|details)|update your (pan|kyc|account|bank)"""
    )

    private val brandRegex = rx(
        """\b(sbi|hdfc|icici|axis|paytm|phonepe|gpay|amazon|flipkart|income tax|rbi|india post|fedex|dhl|bsnl|jio|airtel)\b"""
    )

    private val urgencyRegex = rx(
        """\b(urgent|urgently|immediately|right now|last chance|final notice|act now|hurry|today only|legal action|arrest|police|court|penalty|blocked|suspended|deactivated)\b|expires?\s+(today|soon)|within\s+\d+\s*(hours?|hrs?|minutes?|mins?)"""
    )

    // SMS text-a check panni result tharum. Text-a edhulayum save pannaadhu.
    fun analyze(text: String): ScamResult {
        var score = 0
        val reasons = mutableListOf<String>()
        val stages = mutableSetOf<ScamStage>()

        fun add(points: Int, reason: String) {
            score += points
            reasons.add("$reason (+$points)")
        }

        // Official website link-a vittuttu, meedham irukkura link-a mattum check pannurom
        val links = linkRegex.findAll(text)
            .map { it.value }
            .filterNot { isTrustedLink(it) }
            .toList()

        if (links.isNotEmpty()) {
            add(15, "Message contains a link")
            stages.add(ScamStage.LINK)
            if (links.any { riskyLinkRegex.containsMatchIn(it) }) {
                add(20, "Link looks risky (shortened, unusual ending or fake-looking name)")
            }
        }

        val textForOtp = text.replace(safeShareWarningRegex, " ")
        if (otpRequestRegex.containsMatchIn(textForOtp)) {
            add(30, "Asks you to share an OTP")
            stages.add(ScamStage.OTP)
        }
        if (pinRegex.containsMatchIn(textForOtp)) {
            add(30, "Asks for PIN, password or card details")
            stages.add(ScamStage.OTP)
        }
        if (paymentRegex.containsMatchIn(text)) {
            add(20, "Asks for a payment or fee")
            stages.add(ScamStage.PAYMENT)
        }
        if (rewardRegex.containsMatchIn(text)) {
            add(20, "Fake-looking reward or prize offer")
            stages.add(ScamStage.OFFER)
        }
        if (kycRegex.containsMatchIn(text)) {
            add(20, "Bank / KYC style warning")
        }
        if (links.isNotEmpty() && brandRegex.containsMatchIn(text)) {
            add(15, "Bank or company name with a link (possible impersonation)")
        }
        if (urgencyRegex.containsMatchIn(text)) {
            add(15, "Uses urgency or threatening language")
            stages.add(ScamStage.PRESSURE)
        }

        val finalScore = minOf(score, 100)
        return ScamResult(finalScore, levelFor(finalScore), reasons, stages)
    }
}