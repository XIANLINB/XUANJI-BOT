package dev.xuanji.api.action;

import dev.xuanji.api.message.MessageChain;

/**
 * 媒体服务抽象 — 统一上传与发送媒体文件。
 */
public interface MediaService {

    /** 上传媒体并返回平台文件标识 */
    MediaRef upload(String url, MediaType type);

    /** 发送已上传的媒体 */
    void send(MediaRef ref, String targetId);

    enum MediaType { IMAGE, VOICE, VIDEO, FILE }

    record MediaRef(String fileInfo, MediaType type) {}
}
