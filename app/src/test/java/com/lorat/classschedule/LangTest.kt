package com.lorat.classschedule

import com.lorat.classschedule.i18n.Lang
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** 语言表的三种命中方式：精确、正则规则、没有翻译时回退中文。 */
class LangTest {

    @After
    fun restore() {
        Lang.mode = 0
    }

    @Test
    fun `static text uses the dictionary`() {
        Lang.mode = 2
        assertEquals("Save", Lang.tr("保存"))
        assertEquals("Global settings", Lang.tr("全局设置"))
    }

    @Test
    fun `dynamic text is translated by rules`() {
        Lang.mode = 2
        assertEquals("Week 6", Lang.tr("第 6 周"))
        assertEquals("17 classes this week · 14 courses · 20 weeks", Lang.tr("本周 17 节课 · 全学期 14 门 · 共 20 周"))
        assertEquals("Weeks 1-6,9-10", Lang.tr("1-6,9-10 周"))
        assertEquals("Periods 3–4", Lang.tr("第3-4节"))
        assertEquals("Deleted “概率论”", Lang.tr("已删除「概率论」"))
        assertEquals("Saved to gallery: a.png", Lang.tr("已保存到相册：a.png"))
    }

    @Test
    fun `unknown text falls back to Chinese`() {
        Lang.mode = 2
        assertEquals("课程名", Lang.tr("课程名"))
        assertEquals("@A101 张老师", Lang.tr("@A101 张老师"))
    }

    @Test
    fun `chinese mode returns everything unchanged`() {
        Lang.mode = 1
        assertEquals("保存", Lang.tr("保存"))
        assertEquals("第 6 周", Lang.tr("第 6 周"))
    }
}
