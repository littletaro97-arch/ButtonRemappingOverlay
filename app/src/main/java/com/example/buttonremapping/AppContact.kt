package com.example.buttonremapping

/**
 * 问题反馈联系渠道。
 *
 * 界面只展示渠道名称与昵称；主页链接仅在点击跳转时使用，
 * 不直接写入界面文案。
 */
object AppContact {
    const val XHS_NAME = "小芋头不会取名"
    const val XHS_ID = "5067916575"
    const val XHS_URL = "https://xhslink.cn/m/5lDUAJeK8dT"
    const val XHS_PACKAGE = "com.xingin.xhs"

    const val BILI_NAME = "小芋头不会取名小号"
    const val BILI_UID = "3546980829629370"
    const val BILI_URL = "https://b23.tv/vAknX7a"
    const val BILI_PACKAGE = "com.bilibili.app.blue"

    const val displayText =
        "问题反馈\n小红书：小红书号：$XHS_ID，昵称：$XHS_NAME\nbilibili：UID：$BILI_UID，昵称：$BILI_NAME"
}
