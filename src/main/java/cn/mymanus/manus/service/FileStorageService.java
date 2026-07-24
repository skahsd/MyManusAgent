package cn.mymanus.manus.service;

import org.springframework.http.MediaType;

import java.io.InputStream;

/**
 * 文件存储服务接口，提供文件存储、下载链接生成及文件内容获取等功能。
 */
public interface FileStorageService {

    /**
     * 下载路径模板，用于构建基于 UUID 的文件下载 URL。
     */
    String DOWNLOAD_PATH = "/content/download/{uuid}";

    /**
     * 预览路径模板，用于构建基于 UUID 的文件在线打开 URL。
     */
    String OPEN_PATH = "/content/open/{uuid}";

    /**
     * 保存文件数据，并返回对应的唯一标识符（UUID）。
     *
     * @param data 文件字节数据
     * @return 文件的唯一标识符（UUID）
     */
    String saveFile(byte[] data);

    /**
     * 生成文件下载链接。
     *
     * @param name 文件原始名称
     * @param uuid 文件唯一标识符
     * @return 下载链接字符串
     */
    String generateDownloadUrl(String name, String uuid);

    /**
     * 生成文件预览或打开链接。
     *
     * @param uuid 文件唯一标识符
     * @return 预览链接字符串
     */
    String generateOpenUrl(String uuid);

    /**
     * 根据文件 UUID 获取可下载的内容对象，包含媒体类型和输入流。
     *
     * @param uuid 文件唯一标识符
     * @return 可下载内容对象，包含媒体类型和输入流
     */
    DownloadTableContent generateDownloadableContent(String uuid);

    /**
     * 可下载内容的数据结构，包含媒体类型和输入流。
     *
     * @param type 媒体类型（如 application/pdf、image/jpeg）
     * @param src  输入流，用于读取文件内容
     */
    record DownloadTableContent(MediaType type, InputStream src) {
    }
}
