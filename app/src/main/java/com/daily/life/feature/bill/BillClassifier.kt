package com.daily.life.feature.bill

class BillClassifier {
    fun classify(counterparty: String, rawText: String, direction: Direction): Category {
        if (direction == Direction.INCOME) {
            return if (containsAny(counterparty, rawText, "转账", "转入", "红包") &&
                !containsAny(counterparty, rawText, "工资", "薪资", "奖金")
            ) {
                Category.TRANSFER
            } else {
                Category.INCOME
            }
        }
        return when {
            containsAny(counterparty, rawText, "转账", "转出", "转给", "红包") -> Category.TRANSFER
            containsAny(counterparty, rawText, "麦当劳", "肯德基", "星巴克", "餐", "饭", "咖啡", "奶茶", "美团", "饿了么", "便利店") -> Category.FOOD
            containsAny(counterparty, rawText, "滴滴", "打车", "地铁", "公交", "高铁", "火车", "航空", "加油", "停车") -> Category.TRANSPORT
            containsAny(counterparty, rawText, "淘宝", "天猫", "京东", "拼多多", "购物", "商城") -> Category.SHOPPING
            containsAny(counterparty, rawText, "电影", "游戏", "音乐", "娱乐", "演出", "影院") -> Category.ENTERTAINMENT
            containsAny(counterparty, rawText, "水费", "电费", "燃气", "话费", "宽带", "物业", "账单") -> Category.BILLS
            containsAny(counterparty, rawText, "医院", "药房", "医疗", "健康", "挂号") -> Category.HEALTH
            containsAny(counterparty, rawText, "学校", "学费", "课程", "教育", "培训", "书店") -> Category.EDUCATION
            else -> Category.OTHER
        }
    }

    private fun containsAny(counterparty: String, rawText: String, vararg keywords: String): Boolean {
        val haystack = "$counterparty $rawText"
        return keywords.any(haystack::contains)
    }
}
