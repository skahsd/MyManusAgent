package cn.mymanus.manus.controller;

import cn.mymanus.manus.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内容控制器，用于处理与文件内容相关的请求，如下载和预览。
 */
@RestController
@RequiredArgsConstructor
public class ContentController {

    /**
     * 文件存储服务，提供文件的存储和检索功能。
     */
    private final FileStorageService fileStorageService;

    /**
     * 下载文件接口。
     *
     * @param uuid 文件唯一标识符。
     * @param name 下载时使用的文件名。
     * @return 包含 InputStreamResource 的 ResponseEntity 对象，表示可下载的文件资源。
     */
    @GetMapping(FileStorageService.DOWNLOAD_PATH)
    public ResponseEntity<InputStreamResource> download(@PathVariable("uuid") String uuid, @RequestParam("name") String name) {
        var dw = this.fileStorageService.generateDownloadableContent(uuid);
        return ResponseEntity.ok()
                .contentType(dw.type())
                .header("Content-disposition", "attachment; filename=" + name)
                .body(new InputStreamResource(dw.src()));
    }

    /**
     * 预览文件接口。
     *
     * @param uuid 文件唯一标识符。
     * @return 包含 InputStreamResource 的 ResponseEntity 对象，表示可预览的文件资源。
     */
    @GetMapping(FileStorageService.OPEN_PATH)
    public ResponseEntity<InputStreamResource> open(@PathVariable("uuid") String uuid) {
        var dw = this.fileStorageService.generateDownloadableContent(uuid);
        return ResponseEntity.ok()
                .contentType(dw.type())
                .body(new InputStreamResource(dw.src()));
    }
}
