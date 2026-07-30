package dev.xuanji.api.enums;

import lombok.Getter;

/**
 * QQ 开放平台 API 错误码枚举
 *
 * <p>基于官方文档 https://bot.q.qq.com/wiki/develop/api-v2/ 完整整理。
 * 涵盖所有 API 分类的错误码，用于统一错误解析、日志记录和排查建议。
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 解析错误码
 * QqApiErrorCode code = QqApiErrorCode.of(40034005);
 * if (code != null) {
 *     log.warn("QQ API 错误: {} - {}", code.getCode(), code.getMessage());
 *     log.warn("排查建议: {}", code.getSuggestion());
 * }
 *
 * // 判断错误类型
 * if (code.isRateLimit()) { ... }
 * if (code.isRetryable()) { ... }
 * if (code.isPermissionDenied()) { ... }
 * if (code.isSecurityHit()) { ... }
 * }</pre>
 *
 * <h3>错误码分类</h3>
 * <ul>
 *   <li>10000-10004: Access Token 错误</li>
 *   <li>10001-12003: 公共错误</li>
 *   <li>22006-50065001: 消息相关错误</li>
 *   <li>301000-301007: 子频道权限错误</li>
 *   <li>302000-302024: 日程错误</li>
 *   <li>304003-304052: 消息错误</li>
 *   <li>306001-306009: 消息撤回错误</li>
 *   <li>501001-501020: 公告错误</li>
 *   <li>502001-502010: 禁言错误</li>
 *   <li>503001-503020: 帖子错误</li>
 *   <li>504001-504004: 消息频率错误</li>
 *   <li>610001-610014: 频道权限错误</li>
 *   <li>620001-620007: 表情表态错误</li>
 *   <li>630001-630008: 互动回调错误</li>
 *   <li>850018-40093002: 富媒体上传错误</li>
 *   <li>1100100-3300006: 安全打击/发消息错误</li>
 * </ul>
 *
 * @see dev.xuanji.adapter.qq.api.QqApiService 使用本枚举解析 API 错误
 */
@Getter
public enum QqApiErrorCode {

    // ==================== Access Token 错误 (10000-10004) ====================

    TOKEN_RATE_LIMIT(100001, "Too many requests", "请求过于频繁，请降低刷新频率"),
    TOKEN_INVALID_APPID(100007, "appid invalid", "AppID 无效或机器人状态异常，请检查 AppID"),
    TOKEN_INVALID_SECRET(100016, "invalid appid or secret", "AppID 或 ClientSecret 不正确，请检查凭证"),
    TOKEN_BOT_NOT_FOUND(10004, "机器人不存在", "请确认 AppID 是否正确"),

    // ==================== 公共错误 (10001-12003) ====================

    UNKNOWN_ACCOUNT(10001, "UnknownAccount", "账号异常，请检查机器人状态"),
    UNKNOWN_CHANNEL(10003, "UnknownChannel", "子频道异常，请检查 channel_id"),
    UNKNOWN_GUILD(10004, "UnknownGuild", "频道异常，请检查 guild_id"),
    CHECK_ADMIN_FAILED(11281, "ErrorCheckAdminFailed", "检查管理员失败，系统错误，最多重试一次"),
    CHECK_ADMIN_NOT_PASS(11282, "ErrorCheckAdminNotPass", "未授予管理员权限，请引导用户授权"),
    WRONG_APPID(11251, "ErrorWrongAppid", "appid 错误，请检查 token"),
    CHECK_APP_PRIVILEGE_FAILED(11252, "ErrorCheckAppPrivilegeFailed", "检查应用权限失败，最多重试一次"),
    CHECK_APP_PRIVILEGE_NOT_PASS(11253, "ErrorCheckAppPrivilegeNotPass", "未获得调用权限，需向平台申请"),
    INTERFACE_FORBIDDEN(11254, "ErrorInterfaceForbidden", "接口被封禁"),
    WRONG_APPID_MISSING(11261, "ErrorWrongAppid", "缺少 appid"),
    NOT_SUPPORT_BOT_TOKEN(11262, "ErrorCheckRobot", "当前接口不支持 Bot Token 调用"),
    CHECK_GUILD_AUTH_FAILED(11263, "ErrorCheckGuildAuth", "检查频道权限失败，最多重试一次"),
    GUILD_AUTH_NOT_PASS(11264, "ErrorGuildAuthNotPass", "频道权限未通过，请引导用户授权"),
    ROBOT_BANNED(11265, "ErrorRobotHasBaned", "机器人已被封禁"),
    WRONG_TOKEN_MISSING(11241, "ErrorWrongToken", "缺少 token"),
    CHECK_TOKEN_FAILED(11242, "ErrorCheckTokenFailed", "校验 token 失败，最多重试一次"),
    CHECK_TOKEN_NOT_PASS(11243, "ErrorCheckTokenNotPass", "token 错误，请检查"),
    CHECK_USER_AUTH_FAILED(11273, "ErrorCheckUserAuth", "不支持 Bearer Token 调用"),
    USER_AUTH_NOT_PASS(11274, "ErrorUserAuthNotPass", "用户授权未给予权限，请重新授权"),
    WRONG_APPID_NO_APPID(11275, "ErrorWrongAppid", "无 appid"),
    INVALID_HTTP_HEADER(11301, "ErrorGetHTTPHeader", "HTTP Header 无效"),
    INVALID_HEADER_UIN(11302, "ErrorGetHeaderUIN", "HTTP Header 无效"),
    GET_NICK_FAILED(11303, "ErrorGetNick", "获取昵称失败"),
    GET_AVATAR_FAILED(11304, "ErrorGetAvatar", "获取头像失败"),
    GET_GUILD_ID_FAILED(11305, "ErrorGetGuildID", "获取频道 ID 失败"),
    GET_GUILD_INFO_FAILED(11306, "ErrorGetGuildInfo", "获取频道信息失败"),
    REPLACE_ID_FAILED(12001, "ReplaceIDFailed", "替换 id 失败"),
    REQUEST_INVALID(12002, "RequestInvalid", "请求体错误"),
    RESPONSE_INVALID(12003, "ResponseInvalid", "回包错误"),
    CHANNEL_RATE_LIMIT(20028, "ChannelHitWriteRateLimit", "子频道消息触发限频"),
    CANNOT_SEND_EMPTY(50006, "CannotSendEmptyMessage", "消息为空"),
    INVALID_FORM_BODY(50035, "InvalidFormBody", "form-data 内容异常"),
    MARKDOWN_ONLY_KEYBOARD(50037, "MarkdownKeyboardOnly", "markdown 消息只支持 markdown 或 keyboard 组合"),
    NOT_SAME_GUILD_CHANNEL(50038, "NotSameGuildChannel", "非同频道同子频道"),
    GET_MESSAGE_FAILED(50039, "GetMessageFailed", "获取消息失败"),
    TEMPLATE_TYPE_ERROR(50040, "TemplateTypeError", "消息模版类型错误"),
    MARKDOWN_EMPTY_VALUE(50041, "MarkdownEmptyValue", "markdown 有空值"),
    MARKDOWN_LIST_MAX(50042, "MarkdownListMax", "markdown 列表长达最大值"),
    GUILD_ID_CONVERT_FAILED(50043, "GuildIdConvertFailed", "guild_id 转换失败"),
    CANNOT_REPLY_SELF(50045, "CannotReplySelf", "不能回复机器人自己产生的消息"),
    NOT_AT_BOT(50046, "NotAtBot", "非 at 机器人消息"),
    NOT_BOT_OR_AT_BOT(50047, "NotBotOrAtBot", "非机器人产生的消息或 at 机器人消息"),
    MESSAGE_ID_EMPTY(50048, "MessageIdEmpty", "message id 不能为空"),
    ONLY_MODIFY_KEYBOARD(50049, "OnlyModifyKeyboard", "只能修改含有 keyboard 元素的消息"),
    KEYBOARD_NOT_EMPTY(50050, "KeyboardNotEmpty", "修改消息时 keyboard 不能为空"),
    ONLY_MODIFY_SELF(50051, "OnlyModifySelf", "只能修改机器人自己发送的消息"),
    MODIFY_MESSAGE_ERROR(50053, "ModifyMessageError", "修改消息错误"),
    MARKDOWN_TEMPLATE_ERROR(50054, "MarkdownTemplateError", "markdown 模版参数错误"),
    INVALID_MARKDOWN_CONTENT(50055, "InvalidMarkdownContent", "无效的 markdown content"),
    MARKDOWN_NOT_ALLOWED(50056, "MarkdownNotAllowed", "不允许发送 markdown content"),
    MARKDOWN_NATIVE_OR_TEMPLATE(50057, "MarkdownNativeOrTemplate", "markdown 参数只支持原生语法或模版二选一"),

    // ==================== 单聊消息错误 ====================

    INPUT_TYPE_ERROR(50059, "InputTypeError", "输入类型错误，请检查输入类型"),
    NO_ARK_PERMISSION(304004, "ARKNotAllowed", "无权限使用该 ARK 模板，请先申请"),
    NO_MARKDOWN_PERMISSION(304036, "MarkdownPermissionDenied", "无 Markdown 模板权限，请先申请"),
    MESSAGE_CONTENT_INVALID(304061, "MessageContentInvalid", "消息内容无效，请检查格式"),
    SUBSCRIBE_BUTTON_MAX(304062, "SubscribeButtonMax", "订阅按钮数量达到上限，请减少按钮"),
    SUBSCRIBE_NOT_AUTHORIZED(304064, "SubscribeNotAuthorized", "订阅消息未授权，请引导用户授权"),
    FILE_INFO_INVALID(304080, "FileInfoInvalid", "文件信息无效，请检查 file_info"),
    MESSAGE_ID_EXPIRED(304103, "MessageIdExpired", "消息 ID 已过期，请尽快回复"),
    GET_BOT_INFO_FAILED(340067, "GetBotInfoFailed", "获取机器人信息失败，请检查状态"),
    MESSAGE_TYPE_INVALID(340069, "MessageTypeInvalid", "消息类型无效，请检查 msg_type"),
    MEDIA_TRANSFER_FAILED(40034004, "MediaTransferFailed", "富媒体信息转存失败，请重试"),
    REPLY_MSG_ID_EXPIRED(40034005, "ReplyMsgIdExpired", "回复消息 msg_id 已过期，请尽快回复"),
    MESSAGE_CONTENT_VIOLATION(40034006, "MessageContentViolation", "消息内容违规，请修改后重试"),
    MARKDOWN_PARAM_EMPTY(40034008, "MarkdownParamEmpty", "markdown 参数有空值"),
    MARKDOWN_PARAM_NEWLINE(40034009, "MarkdownParamNewline", "markdown 参数有换行符，请移除"),
    MARKDOWN_PARAM_HAS_SYNTAX(40034010, "MarkdownParamHasSyntax", "模版参数中不能含有 markdown 语法"),
    INVALID_MARKDOWN(40034011, "InvalidMarkdown", "无效的 markdown 内容，请检查语法"),
    MSG_ID_INVALID(40034024, "MsgIdInvalid", "msg_id 无效或越权"),
    EVENT_ID_INVALID(40034025, "EventIdInvalid", "event_id 无效"),
    EVENT_ID_EXPIRED(40034026, "EventIdExpired", "event_id 已过期，请尽快回复"),
    EVENT_NOT_SUPPORT_REPLY(40034027, "EventNotSupportReply", "该事件不支持回复消息"),
    KEYBOARD_ROW_COL_LIMIT(40034029, "KeyboardRowColLimit", "内联键盘行/列超限，请减少按钮"),
    PROACTIVE_RATE_LIMIT(40034100, "ProactiveRateLimit", "主动消息发送超过频控限制"),
    PROACTIVE_NO_PERMISSION(40034105, "ProactiveNoPermission", "主动消息发送失败，无权限"),
    COMMAND_TYPE_NOT_SUPPORT(40034106, "CommandTypeNotSupport", "消息不支持该指令类型"),
    COMMAND_PARAM_TOO_LONG(40034108, "CommandParamTooLong", "指令参数长度超限"),
    COMMAND_PARAM_PARSE_ERROR(40034109, "CommandParamParseError", "指令参数解析失败"),
    WAKEUP_LIMIT(40034122, "WakeupLimit", "召回消息已达区间上限"),
    WAKEUP_NOT_SUPPORT(40034123, "WakeupNotSupport", "不支持召回消息"),
    MARKDOWN_PARAM_ERROR(40034124, "MarkdownParamError", "markdown 消息参数错误"),
    NO_MARKDOWN_TEMPLATE_PERMISSION(40034127, "NoMarkdownTemplatePermission", "无 markdown 模板权限"),
    PASSIVE_REPLY_TIMEOUT(40034128, "PassiveReplyTimeout", "被动回复时间或次数超限"),
    NO_FRIEND_RELATION(40054004, "NoFriendRelation", "无好友关系，请先添加好友"),
    MESSAGE_DEDUP(40054005, "MessageDedup", "消息被去重，请使用不同 msgseq"),
    VERIFY_FRIEND_FAILED(40054006, "VerifyFriendFailed", "验证好友关系失败，请重试"),
    MESSAGE_TOO_LONG(40054007, "MessageTooLong", "消息长度超限，请缩短内容"),
    USER_REJECT_MESSAGE(40054013, "UserRejectMessage", "用户拒收消息"),
    BOT_OFFLINE(40054016, "BotOffline", "机器人已下线，请检查状态"),
    MESSAGE_TOO_LONG_OR_ABNORMAL(40054018, "MessageTooLongOrAbnormal", "消息过长或异常"),
    C2C_SEND_ERROR(50055002, "C2cSendError", "单聊消息发送异常，请稍后重试"),

    // ==================== 群聊消息错误 ====================

    KEYBOARD_STYLE_ERROR(305007, "KeyboardStyleError", "键盘样式参数错误"),
    BOT_MUTED(40054002, "BotMuted", "机器人被禁言，请等待解禁"),
    BOT_NOT_IN_GROUP(40054003, "BotNotInGroup", "机器人不是群成员，请先加入群聊"),
    URL_NOT_ALLOWED(40054010, "UrlNotAllowed", "不允许发送 URL，请移除"),
    GROUP_SEND_ERROR(50055001, "GroupSendError", "群聊消息发送异常，请稍后重试"),
    ARK_SEND_ERROR(50055006, "ArkSendError", "ARK 消息发送异常，请稍后重试"),

    // ==================== 流式消息错误 ====================

    STREAM_PREFIX_MODIFY(40007, "StreamPrefixModify", "已下发内容前缀不可修改"),
    INTERNAL_ERROR(50001, "InternalError", "服务内部错误，请稍后重试"),
    STREAM_RATE_LIMIT(50002, "StreamRateLimit", "流式消息频率限制"),

    // ==================== 撤回消息错误 ====================

    INVALID_USER_OPENID(306009, "InvalidUserOpenid", "用户 openid 无效"),
    INVALID_PARAM(40061001, "InvalidParam", "请求参数无效"),
    INVALID_MSG_ID(40061002, "InvalidMsgId", "msgid 无效"),
    NO_RECALL_PERMISSION(40062003, "NoRecallPermission", "无操作权限"),
    RECALL_TIMEOUT(40064004, "RecallTimeout", "已超出撤回时限（2 分钟）"),
    GROUP_RECALL_FAILED(50065001, "GroupRecallFailed", "群聊消息撤回失败，请稍后重试"),

    // ==================== 子频道权限错误 (301000-301007) ====================

    CHANNEL_PERMISSION_PARAM_ERROR(301000, "ChannelPermissionParamError", "参数错误"),
    CHANNEL_QUERY_ERROR(301001, "ChannelQueryError", "查询频道信息错误"),
    CHANNEL_PERMISSION_QUERY_ERROR(301002, "ChannelPermissionQueryError", "查询子频道权限错误"),
    CHANNEL_PERMISSION_MODIFY_ERROR(301003, "ChannelPermissionModifyError", "修改子频道权限错误"),
    PRIVATE_CHANNEL_MEMBER_LIMIT(301004, "PrivateChannelMemberLimit", "私密子频道关联人数到达上限"),
    RPC_FAILED(301005, "RpcFailed", "调用 Rpc 服务失败"),
    NOT_GUILD_MEMBER(301006, "NotGuildMember", "非群成员没有查询权限"),
    PARAM_COUNT_LIMIT(301007, "ParamCountLimit", "参数超过数量限制"),

    // ==================== 日程错误 (302000-302024) ====================

    SCHEDULE_PARAM_ERROR(302000, "ScheduleParamError", "参数错误"),
    SCHEDULE_GUILD_QUERY_ERROR(302001, "ScheduleGuildQueryError", "查询频道信息错误"),
    SCHEDULE_LIST_FAILED(302002, "ScheduleListFailed", "查询日程列表失败"),
    SCHEDULE_QUERY_FAILED(302003, "ScheduleQueryFailed", "查询日程失败"),
    SCHEDULE_MODIFY_FAILED(302004, "ScheduleModifyFailed", "修改日程失败"),
    SCHEDULE_DELETE_FAILED(302005, "ScheduleDeleteFailed", "删除日程失败"),
    SCHEDULE_CREATE_FAILED(302006, "ScheduleCreateFailed", "创建日程失败"),
    SCHEDULE_GET_CREATOR_FAILED(302007, "ScheduleGetCreatorFailed", "获取创建者信息失败"),
    SCHEDULE_CHANNEL_ID_EMPTY(302008, "ScheduleChannelIdEmpty", "子频道 ID 不能为空"),
    SCHEDULE_SYSTEM_ERROR(302009, "ScheduleSystemError", "频道系统错误"),
    SCHEDULE_NO_PERMISSION(302010, "ScheduleNoPermission", "暂无修改日程权限"),
    SCHEDULE_DELETED(302011, "ScheduleDeleted", "日程活动已被删除"),
    SCHEDULE_DAILY_LIMIT(302012, "ScheduleDailyLimit", "每天只能创建 10 个日程"),
    SCHEDULE_SECURITY_HIT(302013, "ScheduleSecurityHit", "创建日程触发安全打击"),
    SCHEDULE_DURATION_LIMIT(302014, "ScheduleDurationLimit", "日程持续时间超过 7 天"),
    SCHEDULE_START_TIME_ERROR(302015, "ScheduleStartTimeError", "开始时间不能早于当前时间"),
    SCHEDULE_END_TIME_ERROR(302016, "ScheduleEndTimeError", "结束时间不能早于开始时间"),
    SCHEDULE_EMPTY(302017, "ScheduleEmpty", "Schedule 对象为空"),
    SCHEDULE_TYPE_CONVERT_ERROR(302018, "ScheduleTypeConvertError", "参数类型转换失败"),
    SCHEDULE_DOWNSTREAM_FAILED(302019, "ScheduleDownstreamFailed", "调用下游失败"),
    SCHEDULE_CONTENT_VIOLATION(302020, "ScheduleContentViolation", "日程内容违规"),
    SCHEDULE_DAILY_ACTIVITY_LIMIT(302021, "ScheduleDailyActivityLimit", "频道内当日新增活动达上限"),
    SCHEDULE_BIND_CHANNEL_ERROR(302022, "ScheduleBindChannelError", "不能绑定非当前频道的子频道"),
    SCHEDULE_JUMP_BIND_ERROR(302023, "ScheduleJumpBindError", "开始时跳转不可绑定日程子频道"),
    SCHEDULE_BIND_CHANNEL_NOT_EXIST(302024, "ScheduleBindChannelNotExist", "绑定的子频道不存在"),

    // ==================== 频道消息错误 (304003-304052) ====================

    URL_NOT_REGISTERED(304003, "URLNotRegistered", "URL 未报备"),
    EMBED_LIMIT_EXCEEDED(304005, "EmbedLimitExceeded", "embed 长度超限"),
    SERVER_CONFIG_ERROR(304006, "ServerConfigError", "后台配置错误"),
    GET_GUILD_ERROR(304007, "GetGuildError", "查询频道异常"),
    GET_BOT_ERROR(304008, "GetBotError", "查询机器人异常"),
    GET_CHANNEL_ERROR(304009, "GetChannelError", "查询子频道异常"),
    IMAGE_TRANSFER_ERROR(304010, "ImageTransferError", "图片转存错误"),
    NO_TEMPLATE(304011, "NoTemplate", "模板不存在"),
    GET_TEMPLATE_ERROR(304012, "GetTemplateError", "取模板错误"),
    TEMPLATE_NO_PERMISSION(304014, "TemplateNoPermission", "没有模板权限"),
    SEND_ERROR(304016, "SendError", "发消息错误"),
    UPLOAD_IMAGE_ERROR(304017, "UploadImageError", "图片上传错误"),
    SESSION_NOT_EXIST(304018, "SessionNotExist", "机器人没连上 gateway"),
    AT_EVERYONE_LIMIT(304019, "AtEveryoneLimit", "@全体成员次数超限"),
    FILE_SIZE_LIMIT(304020, "FileSizeLimit", "文件大小超限"),
    GET_FILE_ERROR(304021, "GetFileError", "下载文件错误"),
    PUSH_TIME_LIMIT(304022, "PushTimeLimit", "推送消息时间限制"),
    PUSH_MSG_AUDIT_PENDING(304023, "PushMsgAuditPending", "推送消息异步调用成功，等待审核"),
    REPLY_MSG_AUDIT_PENDING(304024, "ReplyMsgAuditPending", "回复消息异步调用成功，等待审核"),
    MESSAGE_BEAT(304025, "MessageBeat", "消息被打击"),
    MSG_ID_ERROR(304026, "MsgIdError", "回复的消息 id 错误"),
    MSG_EXPIRED(304027, "MsgExpired", "回复的消息过期"),
    MSG_PROTECT(304028, "MsgProtect", "非 At 当前用户的消息不允许回复"),
    CORPUS_ERROR(304029, "CorpusError", "调语料服务错误"),
    CORPUS_NOT_MATCH(304030, "CorpusNotMatch", "语料不匹配"),
    DM_CLOSED(304031, "DmClosed", "私信已关闭"),
    DM_NOT_EXIST(304032, "DmNotExist", "私信不存在"),
    DM_PULL_ERROR(304033, "DmPullError", "拉私信错误"),
    NOT_DM_MEMBER(304034, "NotDmMember", "不是私信成员"),
    PUSH_CHANNEL_LIMIT(304035, "PushChannelLimit", "推送消息超过子频道数量限制"),
    NO_BUTTON_PERMISSION(304037, "NoButtonPermission", "没有发消息按钮组件的权限"),
    BUTTON_NOT_EXIST(304038, "ButtonNotExist", "消息按钮组件不存在"),
    BUTTON_PARSE_ERROR(304039, "ButtonParseError", "消息按钮组件解析错误"),
    BUTTON_CONTENT_ERROR(304040, "ButtonContentError", "消息按钮组件内容错误"),
    GET_MSG_SETTING_ERROR(304044, "GetMsgSettingError", "取消息设置错误"),
    CHANNEL_PUSH_RATE_LIMIT(304045, "ChannelPushRateLimit", "子频道主动消息数限频"),
    CHANNEL_PUSH_NOT_ALLOWED(304046, "ChannelPushNotAllowed", "不允许在此子频道发主动消息"),
    PUSH_CHANNEL_COUNT_LIMIT(304047, "PushChannelCountLimit", "主动消息推送超过限制的子频道数"),
    GUILD_PUSH_NOT_ALLOWED(304048, "GuildPushNotAllowed", "不允许在此频道发主动消息"),
    DM_PUSH_RATE_LIMIT(304049, "DmPushRateLimit", "私信主动消息数限频"),
    DM_PUSH_TOTAL_LIMIT(304050, "DmPushTotalLimit", "私信主动消息总量限频"),
    MSG_SETTING_GUIDE_ERROR(304051, "MsgSettingGuideError", "消息设置引导请求构造错误"),
    MSG_SETTING_GUIDE_RATE_LIMIT(304052, "MsgSettingGuideRateLimit", "发消息设置引导超频"),

    // ==================== 频道消息撤回错误 (306001-306006) ====================

    CHANNEL_RECALL_PARAM_ERROR(306001, "ChannelRecallParamError", "撤回消息参数错误"),
    CHANNEL_RECALL_MSG_ID_ERROR(306002, "ChannelRecallMsgIdError", "消息 id 错误"),
    CHANNEL_RECALL_GET_MSG_FAILED(306003, "ChannelRecallGetMsgFailed", "获取消息错误（可重试）"),
    CHANNEL_RECALL_NO_PERMISSION(306004, "ChannelRecallNoPermission", "没有撤回此消息的权限"),
    CHANNEL_RECALL_FAILED(306005, "ChannelRecallFailed", "消息撤回失败（可重试）"),
    CHANNEL_RECALL_GET_CHANNEL_FAILED(306006, "ChannelRecallGetChannelFailed", "获取子频道失败（可重试）"),

    // ==================== 公告错误 (501001-501020) ====================

    ANNOUNCE_PARAM_ERROR(501001, "AnnounceParamError", "参数校验失败"),
    ANNOUNCE_CREATE_FAILED(501002, "AnnounceCreateFailed", "创建子频道公告失败（可重试）"),
    ANNOUNCE_DELETE_FAILED(501003, "AnnounceDeleteFailed", "删除子频道公告失败（可重试）"),
    ANNOUNCE_GET_GUILD_FAILED(501004, "AnnounceGetGuildFailed", "获取频道信息失败（可重试）"),
    ANNOUNCE_MSG_ID_ERROR(501005, "AnnounceMsgIdError", "MessageID 错误"),
    ANNOUNCE_GLOBAL_CREATE_FAILED(501006, "AnnounceGlobalCreateFailed", "创建频道全局公告失败（可重试）"),
    ANNOUNCE_GLOBAL_DELETE_FAILED(501007, "AnnounceGlobalDeleteFailed", "删除频道全局公告失败（可重试）"),
    ANNOUNCE_MSG_ID_NOT_EXIST(501008, "AnnounceMsgIdNotExist", "MessageID 不存在"),
    ANNOUNCE_MSG_ID_PARSE_FAILED(501009, "AnnounceMsgIdParseFailed", "MessageID 解析失败"),
    ANNOUNCE_NOT_CHANNEL_MSG(501010, "AnnounceNotChannelMsg", "此条消息非子频道内消息"),
    PIN_CREATE_FAILED(501011, "PinCreateFailed", "创建精华消息失败（可重试）"),
    PIN_DELETE_FAILED(501012, "PinDeleteFailed", "删除精华消息失败（可重试）"),
    PIN_MAX_LIMIT(501013, "PinMaxLimit", "精华消息超过最大数量"),
    ANNOUNCE_SECURITY_HIT(501014, "AnnounceSecurityHit", "安全打击"),
    ANNOUNCE_MSG_NOT_ALLOWED(501015, "AnnounceMsgNotAllowed", "此消息不允许设置"),
    ANNOUNCE_CHANNEL_RECOMMEND_LIMIT(501016, "AnnounceChannelRecommendLimit", "频道公告子频道推荐超过最大数量"),
    ANNOUNCE_NOT_ADMIN(501017, "AnnounceNotAdmin", "非频道主或管理员"),
    ANNOUNCE_RECOMMEND_CHANNEL_INVALID(501018, "AnnounceRecommendChannelInvalid", "推荐子频道 ID 无效"),
    ANNOUNCE_TYPE_ERROR(501019, "AnnounceTypeError", "公告类型错误"),
    ANNOUNCE_RECOMMEND_CREATE_FAILED(501020, "AnnounceRecommendCreateFailed", "创建推荐子频道类型频道公告失败"),

    // ==================== 禁言错误 (502001-502010) ====================

    MUTE_GUILD_ID_INVALID(502001, "MuteGuildIdInvalid", "频道 id 无效"),
    MUTE_GUILD_ID_EMPTY(502002, "MuteGuildIdEmpty", "频道 id 为空"),
    MUTE_USER_ID_INVALID(502003, "MuteUserIdInvalid", "用户 id 无效"),
    MUTE_USER_ID_EMPTY(502004, "MuteUserIdEmpty", "用户 id 为空"),
    MUTE_TIMESTAMP_INVALID(502005, "MuteTimestampInvalid", "timestamp 不合法"),
    MUTE_TIMESTAMP_ERROR(502006, "MuteTimestampError", "timestamp 无效"),
    MUTE_PARAM_CONVERT_ERROR(502007, "MuteParamConvertError", "参数转换错误"),
    MUTE_RPC_FAILED(502008, "MuteRpcFailed", "rpc 调用失败"),
    MUTE_SECURITY_HIT(502009, "MuteSecurityHit", "安全打击"),
    MUTE_HEADER_ERROR(502010, "MuteHeaderError", "请求头错误"),

    // ==================== 帖子错误 (503001-503020) ====================

    FORUM_GUILD_ID_INVALID(503001, "ForumGuildIdInvalid", "频道 id 无效"),
    FORUM_GUILD_ID_EMPTY(503002, "ForumGuildIdEmpty", "频道 id 为空"),
    FORUM_GET_CHANNEL_FAILED(503003, "ForumGetChannelFailed", "获取子频道信息失败"),
    FORUM_PUBLISH_RATE_LIMIT(503004, "ForumPublishRateLimit", "超出发布帖子的频次限制"),
    FORUM_TITLE_EMPTY(503005, "ForumTitleEmpty", "帖子标题为空"),
    FORUM_CONTENT_EMPTY(503006, "ForumContentEmpty", "帖子内容为空"),
    FORUM_THREAD_ID_EMPTY(503007, "ForumThreadIdEmpty", "帖子 ID 为空"),
    FORUM_GET_UIN_FAILED(503008, "ForumGetUinFailed", "获取 X-Uin 失败"),
    FORUM_THREAD_ID_INVALID(503009, "ForumThreadIdInvalid", "帖子 ID 无效或不合法"),
    FORUM_GET_TINY_ID_FAILED(503010, "ForumGetTinyIdFailed", "通过 Uin 获取 TinyID 失败"),
    FORUM_TIMESTAMP_INVALID(503011, "ForumTimestampInvalid", "帖子 ID 里的时间戳无效"),
    FORUM_THREAD_NOT_EXIST(503012, "ForumThreadNotExist", "帖子不存在或已删除"),
    FORUM_SERVER_ERROR(503013, "ForumServerError", "服务器内部错误"),
    FORUM_JSON_PARSE_ERROR(503014, "ForumJsonParseError", "帖子 JSON 内容解析失败"),
    FORUM_CONTENT_CONVERT_ERROR(503015, "ForumContentConvertError", "帖子内容转换失败"),
    FORUM_LINK_LIMIT(503016, "ForumLinkLimit", "链接数量超过限制"),
    FORUM_WORD_LIMIT(503017, "ForumWordLimit", "字数超过限制"),
    FORUM_IMAGE_LIMIT(503018, "ForumImageLimit", "图片数量超过限制"),
    FORUM_VIDEO_LIMIT(503019, "ForumVideoLimit", "视频数量超过限制"),
    FORUM_TITLE_LENGTH_LIMIT(503020, "ForumTitleLengthLimit", "标题长度超过限制"),

    // ==================== 消息频率错误 (504001-504004) ====================

    MSG_FREQ_PARAM_ERROR(504001, "MsgFreqParamError", "请求参数无效"),
    MSG_FREQ_GET_HEADER_FAILED(504002, "MsgFreqGetHeaderFailed", "获取 HTTP 头失败"),
    MSG_FREQ_GET_BOT_UIN_ERROR(504003, "MsgFreqGetBotUinError", "获取 BOT UIN 错误"),
    MSG_FREQ_GET_SETTING_ERROR(504004, "MsgFreqGetSettingError", "获取消息频率设置信息错误"),

    // ==================== 频道权限错误 (610001-610014) ====================

    API_PERM_GET_GUILD_FAILED(610001, "ApiPermGetGuildFailed", "获取频道 ID 失败"),
    API_PERM_GET_HEADER_FAILED(610002, "ApiPermGetHeaderFailed", "获取 HTTP 头失败"),
    API_PERM_GET_BOT_FAILED(610003, "ApiPermGetBotFailed", "获取机器人号码失败"),
    API_PERM_GET_ROLE_FAILED(610004, "ApiPermGetRoleFailed", "获取机器人角色失败"),
    API_PERM_GET_ROLE_ERROR(610005, "ApiPermGetRoleError", "获取机器人角色内部错误"),
    API_PERM_LIST_FAILED(610006, "ApiPermListFailed", "拉取机器人权限列表失败"),
    API_PERM_BOT_NOT_IN_GUILD(610007, "ApiPermBotNotInGuild", "机器人不在频道内"),
    API_PERM_INVALID_PARAM(610008, "ApiPermInvalidParam", "无效参数"),
    API_PERM_GET_API_DETAIL_FAILED(610009, "ApiPermGetApiDetailFailed", "获取 API 接口详情失败"),
    API_PERM_ALREADY_AUTHORIZED(610010, "ApiPermAlreadyAuthorized", "API 接口已授权"),
    API_PERM_GET_BOT_INFO_FAILED(610011, "ApiPermGetBotInfoFailed", "获取机器人信息失败"),
    API_PERM_RATE_LIMIT_FAILED(610012, "ApiPermRateLimitFailed", "限频失败"),
    API_PERM_ALREADY_RATE_LIMITED(610013, "ApiPermAlreadyRateLimited", "已限频"),
    API_PERM_AUTH_LINK_SEND_FAILED(610014, "ApiPermAuthLinkSendFailed", "api 授权链接发送失败"),

    // ==================== 表情表态错误 (620001-620007) ====================

    REACTION_INVALID_PARAM(620001, "ReactionInvalidParam", "表情表态无效参数"),
    REACTION_TYPE_LIMIT(620002, "ReactionTypeLimit", "已达到表情反应类型数量上限"),
    REACTION_ALREADY_SET(620003, "ReactionAlreadySet", "已经设置过该表情表态"),
    REACTION_NOT_SET(620004, "ReactionNotSet", "没有设置过该表情表态"),
    REACTION_NO_PERMISSION(620005, "ReactionNoPermission", "没有权限设置表情表态"),
    REACTION_RATE_LIMIT(620006, "ReactionRateLimit", "操作限频"),
    REACTION_FAILED(620007, "ReactionFailed", "表情表态操作失败，请重试"),

    // ==================== 互动回调错误 (630001-630008) ====================

    INTERACTION_INVALID_PARAM(630001, "InteractionInvalidParam", "互动回调参数无效"),
    INTERACTION_GET_APPID_FAILED(630002, "InteractionGetAppidFailed", "获取 AppID 失败"),
    INTERACTION_APPID_MISMATCH(630003, "InteractionAppidMismatch", "AppID 不匹配"),
    INTERACTION_SET_DATA_FAILED(630004, "InteractionSetDataFailed", "设置互动数据失败"),
    INTERACTION_GET_DATA_FAILED(630005, "InteractionGetDataFailed", "获取互动数据失败"),
    INTERACTION_GET_HEADER_APPID_FAILED(630006, "InteractionGetHeaderAppidFailed", "读取请求 AppID 失败"),
    INTERACTION_DATA_TOO_LARGE(630007, "InteractionDataTooLarge", "互动回调数据太大"),
    INTERACTION_PREPROCESS_FAILED(630008, "InteractionPreprocessFailed", "互动预处理失败"),

    // ==================== 富媒体上传错误 ====================

    MEDIA_BOT_MUTED(850018, "MediaBotMuted", "群被禁言或者机器人被禁言"),
    MEDIA_FORMAT_NOT_SUPPORT(850019, "MediaFormatNotSupport", "不支持的文件格式"),
    MEDIA_DOWNLOAD_FAILED(850026, "MediaDownloadFailed", "下载原始文件失败，请检查 URL"),
    MEDIA_SIZE_LIMIT(850031, "MediaSizeLimit", "上传文件超过大小限制"),
    MEDIA_TIMEOUT(850027, "MediaTimeout", "发送数据超时，请稍后重试"),
    MEDIA_NOT_SUPPORT(10000, "MediaNotSupport", "不支持的操作"),
    MEDIA_UPLOAD_FAILED(40093001, "MediaUploadFailed", "文件上传失败（BDH 通道异常），请重试"),
    MEDIA_DAILY_LIMIT(40093002, "MediaDailyLimit", "超过今天发送文件容量上限"),

    // ==================== 分享链接错误 ====================

    SHARE_PARAM_ERROR(10001, "ShareParamError", "请求参数异常"),
    SHARE_HEADER_ERROR(10002, "ShareHeaderError", "请求头异常"),
    SHARE_BOT_INFO_ERROR(10003, "ShareBotInfoError", "查询机器人信息异常"),
    SHARE_GET_UIN_FAILED(10004, "ShareGetUinFailed", "从协议头获取 uin 失败"),
    SHARE_ARK_FAILED(11004, "ShareArkFailed", "生成分享 ARK 失败"),

    // ==================== 安全打击/发消息错误 (1000000+) ====================

    SECURITY_RATE_LIMIT(1100100, "SecurityRateLimit", "安全打击：消息被限频"),
    SECURITY_SENSITIVE(1100101, "SecuritySensitive", "安全打击：内容涉及敏感"),
    SECURITY_NO_ACCESS(1100102, "SecurityNoAccess", "安全打击：暂未获得新功能体验资格"),
    SECURITY_HIT(1100103, "SecurityHit", "安全打击"),
    SECURITY_GROUP_INVALID(1100104, "SecurityGroupInvalid", "安全打击：该群已失效或不存在"),
    SEND_INTERNAL_ERROR(1100300, "SendInternalError", "系统内部错误"),
    SEND_NOT_GROUP_MEMBER(1100301, "SendNotGroupMember", "调用方不是群成员"),
    SEND_GET_CHANNEL_NAME_FAILED(1100302, "SendGetChannelNameFailed", "获取指定频道名称失败"),
    SEND_NOT_ADMIN(1100303, "SendNotAdmin", "主页频道非管理员不允许发消息"),
    SEND_AT_AUTH_FAILED(1100304, "SendAtAuthFailed", "@次数鉴权失败"),
    SEND_TINY_ID_CONVERT_FAILED(1100305, "SendTinyIdConvertFailed", "TinyId 转换 Uin 失败"),
    SEND_NOT_PRIVATE_MEMBER(1100306, "SendNotPrivateMember", "非私有频道成员"),
    SEND_NOT_WHITELIST(1100307, "SendNotWhitelist", "非白名单应用子频道"),
    SEND_CHANNEL_RATE_LIMIT(1100308, "SendChannelRateLimit", "触发频道内限频"),
    SEND_OTHER_ERROR(1100499, "SendOtherError", "其他错误"),
    EDIT_SECURITY_HIT(3300006, "EditSecurityHit", "安全打击");

    // ==================== 枚举结束 ====================

    /** QQ 平台错误码（整数） */
    private final int code;

    /** 错误名称（英文标识） */
    private final String name;

    /** 错误消息（与 name 相同，用于兼容） */
    private final String message;

    /** 排查建议（中文，指导开发者如何解决问题） */
    private final String suggestion;

    QqApiErrorCode(int code, String name, String suggestion) {
        this.code = code;
        this.name = name;
        this.message = name;
        this.suggestion = suggestion;
    }

    /**
     * 根据错误码查找枚举
     *
     * @param code QQ 平台错误码
     * @return 对应的枚举值，未找到返回 null
     */
    public static QqApiErrorCode of(int code) {
        for (QqApiErrorCode e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }

    /**
     * 根据错误码获取排查建议
     *
     * @param code QQ 平台错误码
     * @return 排查建议，未找到返回通用提示
     */
    public static String getSuggestion(int code) {
        QqApiErrorCode e = of(code);
        return e != null ? e.suggestion : "未知错误码 " + code + "，请查阅官方文档";
    }

    /**
     * 根据错误码获取错误名称
     *
     * @param code QQ 平台错误码
     * @return 错误名称，未找到返回 "UNKNOWN"
     */
    public static String getName(int code) {
        QqApiErrorCode e = of(code);
        return e != null ? e.name : "UNKNOWN";
    }

    /**
     * 是否为可重试的错误
     *
     * <p>可重试的错误通常是临时性的（如服务端错误、限流），重试可能成功。
     *
     * @return true=可以重试，false=不应重试（如参数错误、权限不足）
     */
    public boolean isRetryable() {
        return switch (code) {
            case 50001, 50002, 850027, 40093001, 306003, 306005, 306006,
                 501002, 501003, 501004, 501006, 501007, 501011, 501012,
                 11281, 11252, 11263, 11242, 50055001, 50055002, 50055006,
                 50065001, 1100300 -> true;
            default -> false;
        };
    }

    /**
     * 是否为频率限制错误
     *
     * @return true=频率限制
     */
    public boolean isRateLimit() {
        return switch (code) {
            case 100001, 20028, 50002, 40034100, 40034128, 40054013,
                 304045, 304049, 304050, 304052, 502006, 503004,
                 620006, 1100100, 1100308 -> true;
            default -> false;
        };
    }

    /**
     * 是否为权限不足错误
     *
     * @return true=权限不足
     */
    public boolean isPermissionDenied() {
        return switch (code) {
            case 11282, 11253, 11254, 11264, 11274, 304004, 304036,
                 304037, 304127, 40034105, 40054010, 306004, 501017,
                 610007, 620005, 630003 -> true;
            default -> false;
        };
    }

    /**
     * 是否为安全打击错误
     *
     * @return true=安全打击
     */
    public boolean isSecurityHit() {
        return switch (code) {
            case 1100100, 1100101, 1100102, 1100103, 1100104, 3300006, 501014, 502009 -> true;
            default -> false;
        };
    }
}
