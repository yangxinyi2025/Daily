package com.daily.life.feature.bill

import org.junit.Assert.assertEquals
import org.junit.Test

class BillClassifierTest {
    private val classifier = BillClassifier()

    @Test
    fun classifiesCommonLocalCategoriesDeterministically() {
        assertEquals(Category.FOOD, classifier.classify("麦当劳", "午餐", Direction.EXPENSE))
        assertEquals(Category.TRANSPORT, classifier.classify("滴滴出行", "打车", Direction.EXPENSE))
        assertEquals(Category.EDUCATION, classifier.classify("学校", "课程费", Direction.EXPENSE))
        assertEquals(Category.INCOME, classifier.classify("工资", "工资收入", Direction.INCOME))
    }

    @Test
    fun unknownExpenseRemainsEditableAsOther() {
        assertEquals(Category.OTHER, classifier.classify("未知商户", "未识别交易", Direction.EXPENSE))
    }

    @Test
    fun transferRuleWinsOverGenericShoppingRule() {
        assertEquals(Category.TRANSFER, classifier.classify("淘宝", "转账给朋友", Direction.EXPENSE))
    }
}
