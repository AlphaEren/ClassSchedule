package com.lorat.classschedule.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 界面语言。0 = 跟随系统，1 = 简体中文，2 = English。
 *
 * 文案没有走 Android 资源，而是以「中文原文」为 key 查表：这样所有已有界面不用改调用点，
 * 只要它们最终是用 [Text] 画出来的。查不到就原样返回中文，所以漏翻只会显示中文，不会出错。
 */
object Lang {
    var mode by mutableStateOf(0)

    fun english(): Boolean = when (mode) {
        1 -> false
        2 -> true
        else -> Locale.getDefault().language.startsWith("en")
    }

    /** 翻译一段界面文案；动态部分由 [EN_RULES] 里的正则处理。 */
    fun tr(text: String): String {
        if (!english()) return text
        EN[text]?.let { return it }
        for (rule in EN_RULES) {
            val match = rule.pattern.matchEntire(text) ?: continue
            return rule.render(match)
        }
        return text
    }
}

/** 一条「整串匹配」的翻译规则，用于拼接出来的动态文案。 */
internal class Rule(val pattern: Regex, val render: (MatchResult) -> String)
