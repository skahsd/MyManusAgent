package cn.mymanus.manus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DialogMessageDTO {
    public static final String TYPE_SERVER = "server";

    // 消息类型E
    private String type = TYPE_SERVER;
    // 消息内容
    private String text;
    // 图片地址
    private String imageUrl;
    // 文件地址
    private String fileUrl;
    // 链接地址
    private String openUrl;
}
