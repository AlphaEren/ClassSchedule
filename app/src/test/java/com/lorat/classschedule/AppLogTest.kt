package com.lorat.classschedule

import com.lorat.classschedule.data.filesToDelete
import com.lorat.classschedule.data.logFileName
import com.lorat.classschedule.data.redactUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 本地日志：文件名、网址脱敏、轮转策略。 */
class AppLogTest {

    @Test
    fun `one log file per day`() {
        assertEquals("classschedule-2026-10-14.log", logFileName("2026-10-14"))
    }

    @Test
    fun `urls keep only host and first path segment`() {
        assertEquals(
            "https://jw.example.edu.cn/Navigation/…",
            redactUrl("https://jw.example.edu.cn/Navigation/Default.htm?token=secret#frag"),
        )
        assertEquals("https://a.example.edu.cn", redactUrl("https://a.example.edu.cn"))
        assertEquals("", redactUrl("   "))
        // 查询串（可能含学号/令牌）绝不留下来
        assertTrue(!redactUrl("https://a.example.edu.cn/x?sid=20260101").contains("sid"))
    }

    @Test
    fun `rotation keeps the newest files and ignores others`() {
        val names = (1..10).map { "classschedule-2026-10-%02d.log".format(it) } + "other.txt"
        val stale = filesToDelete(names, keep = 7)
        assertEquals(3, stale.size)
        assertTrue(stale.contains("classschedule-2026-10-01.log"))
        assertTrue(stale.contains("classschedule-2026-10-03.log"))
        assertTrue(!stale.contains("classschedule-2026-10-04.log"))
        assertTrue(!stale.contains("other.txt"))
    }
}
