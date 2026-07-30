package dev.xuanji.api.annotation;

/**
 * @机器人模式
 */
public enum AtMode {
    /** 不关心是否 @ */
    IGNORE,
    /** 必须 @机器人才触发 */
    NEED,
    /** 不能 @机器人（普通消息才触发） */
    NOT
}
