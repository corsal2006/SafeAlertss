package com.siddesh.smsalerts.data.model

enum class EmergencyType(
    val title: String,
    val description: String,
    val symbol: String
) {
    MEDICAL(
        title = "Medical Emergency",
        description = "Sudden illness, medical distress, or severe injury requiring immediate assistance.",
        symbol = "🚑"
    ),
    ACCIDENT(
        title = "Accident",
        description = "Vehicle crash, collision, or physical accident with possible injury.",
        symbol = "🚗"
    ),
    FIRE(
        title = "Fire",
        description = "Fire breakout, smoke hazard, or burning danger requiring evacuation.",
        symbol = "🔥"
    ),
    CRIME(
        title = "Crime / Threat",
        description = "Facing active security risk, violence, stalking, theft, or assault threat.",
        symbol = "🚨"
    ),
    PERSONAL(
        title = "Personal Emergency",
        description = "Immediate personal safety crisis or urgent distress requiring direct help.",
        symbol = "🆘"
    ),
    MISSING(
        title = "Missing / Lost Person",
        description = "Need urgent help locating someone or currently lost in an unfamiliar place.",
        symbol = "👤"
    ),
    DISASTER(
        title = "Natural Disaster",
        description = "Affected by flood, earthquake, cyclone, wildfire, or severe weather crisis.",
        symbol = "🌊"
    ),
    OTHER(
        title = "Other Emergency",
        description = "Unforeseen urgent situation requiring immediate emergency attention.",
        symbol = "⚠️"
    );

    val defaultMessage: String
        get() = AlertTemplateProvider.getTemplateForType(this)

    companion object {
        fun fromNameOrTitle(input: String): EmergencyType {
            return entries.find {
                it.name.equals(input, ignoreCase = true) ||
                        it.title.equals(input, ignoreCase = true)
            } ?: OTHER
        }

        fun fromTitle(title: String): EmergencyType = fromNameOrTitle(title)
    }
}
