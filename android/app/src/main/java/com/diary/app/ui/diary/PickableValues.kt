package com.diary.app.ui.diary

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoodBad
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

enum class Mood(val key: String, val icon: ImageVector, val emoji: String, val label: String) {
    HAPPY("happy", Icons.Filled.SentimentVerySatisfied, "😊", "开心"),
    CALM("calm", Icons.Filled.SentimentSatisfied, "😌", "平静"),
    SAD("sad", Icons.Filled.SentimentVeryDissatisfied, "😢", "难过"),
    ANGRY("angry", Icons.Filled.MoodBad, "😠", "生气"),
    TIRED("tired", Icons.Filled.Bedtime, "😪", "疲惫"),
    EXCITED("excited", Icons.Filled.Celebration, "🤩", "兴奋"),
    TOUCHED("touched", Icons.Filled.Favorite, "🥹", "感动"),
    NEUTRAL("neutral", Icons.Filled.SentimentNeutral, "😑", "平淡"),
    UPSET("upset", Icons.Filled.SentimentDissatisfied, "😞", "委屈"),

    ;

    companion object {
        fun fromKey(key: String?): Mood? = entries.firstOrNull { it.key == key }
    }
}

enum class Weather(val key: String, val icon: ImageVector, val emoji: String, val label: String) {
    SUNNY("sunny", Icons.Filled.WbSunny, "☀️", "晴"),
    CLOUDY("cloudy", Icons.Filled.Cloud, "☁️", "多云"),
    RAINY("rainy", Icons.Filled.Umbrella, "🌧️", "雨"),
    SNOWY("snowy", Icons.Filled.AcUnit, "❄️", "雪"),
    WINDY("windy", Icons.Filled.Air, "💨", "风"),
    FOGGY("foggy", Icons.Filled.BlurOn, "🌫️", "雾"),
    THUNDER("thunder", Icons.Filled.Thunderstorm, "⛈️", "雷"),
    NIGHT("night", Icons.Filled.NightsStay, "🌙", "夜"),
    HOT("hot", Icons.Filled.DeviceThermostat, "🌡️", "热"),

    ;

    companion object {
        fun fromKey(key: String?): Weather? = entries.firstOrNull { it.key == key }
    }
}
