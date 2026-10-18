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
        // 课程详情面板
        assertEquals("This week", Lang.tr("仅本周"))
        assertEquals("All weeks", Lang.tr("所有周"))
        assertEquals("Week 9 only", Lang.tr("仅第 9 周"))
        assertEquals("Periods 1–2", Lang.tr("第 1 - 2 节"))
        assertEquals("Note (optional)", Lang.tr("备注（可不填）"))
        assertEquals("Removed “羽毛球” from week 8", Lang.tr("已删除第 8 周的「羽毛球」"))
        assertEquals("Credits 3", Lang.tr("学分 3"))
        assertEquals("copy", Lang.tr("副本"))
        // 删除范围面板
        assertEquals("Week 8 Mon only", Lang.tr("仅第 8 周周一的这节课"))
        assertEquals("All Mon classes at this time", Lang.tr("全部周一同老师同地点的这节课"))
        assertEquals("All time slots of this course", Lang.tr("这门课程的全部时间段"))
        assertEquals(
            "Removed “羽毛球” from week 8",
            Lang.tr("已删除第 8 周「羽毛球」的这节课"),
        )
        assertEquals(
            "Removed the Tue class of “羽毛球”",
            Lang.tr("已删除「羽毛球」在周二的这节课"),
        )
        assertEquals("Note saved", Lang.tr("备注已保存"))
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
