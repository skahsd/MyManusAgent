package cn.mymanus.manus.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.mymanus.manus.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private final Tika tika = new Tika();
    @Value("${file.base:/tmp/}")
    private String baseFolder;
    @Value("${file.domain:http://localhost:18081}")
    private String domain;

    @Override
    public String saveFile(byte[] data) {
        var uuid = IdUtil.fastSimpleUUID();
        FileUtil.writeBytes(data, this.getFilePath(uuid));
        return uuid;
    }

    private String getFilePath(String uuid) {
        return baseFolder + uuid;
    }

    @Override
    public String generateDownloadUrl(String name, String uuid) {
        return StrUtil.format("{}{}?name={}",
                domain,
                StrUtil.format(DOWNLOAD_PATH, Map.of("uuid", uuid)),
                name);
    }

    @Override
    public String generateOpenUrl(String uuid) {
        return StrUtil.format("{}{}",
                domain,
                StrUtil.format(OPEN_PATH, Map.of("uuid", uuid)));
    }

    @Override

    public DownloadTableContent generateDownloadableContent(String uuid) {
        // 获取文件输入流
        var is = FileUtil.getInputStream(this.getFilePath(uuid));
        try {
            // 通过tika获取文件类型
            String mimeType = tika.detect(is);
            // 创建下载内容对象返回
            return new DownloadTableContent(new MediaType(MediaType.valueOf(mimeType), StandardCharsets.UTF_8), is);
        } catch (Exception e) {
            log.error("IOException in generateDownloadableContent", e);
            return new DownloadTableContent(MediaType.APPLICATION_OCTET_STREAM, is);
        }
    }
}
