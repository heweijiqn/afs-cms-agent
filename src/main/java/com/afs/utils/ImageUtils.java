package com.afs.utils;

import com.luciad.imageio.webp.WebPWriteParam;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;

@Slf4j
public class ImageUtils {

    public static void toWebp(String oldFile, String newFile){
        log.info("ImageUtils toWebp oldFile={}, newFile={}", oldFile, newFile);
        try {
            // 获取原始文件的编码
            BufferedImage image = ImageIO.read(new File(oldFile));
            // 创建WebP ImageWriter实例
            ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").next();
            // 配置编码参数
            WebPWriteParam writeParam = new WebPWriteParam(writer.getLocale());
            // 设置压缩模式
            writeParam.setCompressionMode(WebPWriteParam.MODE_DEFAULT);
            // 配置ImageWriter输出
            writer.setOutput(new FileImageOutputStream(new File(newFile)));
            // 进行编码，重新生成新图片
            writer.write(null, new IIOImage(image, null, null), writeParam);
        } catch (Exception e) {
            log.error("ImageUtils toWebp error", e);
        }
    }

    public static String replaceSuffix(String localFile) {
        int dotIndex = localFile.lastIndexOf('.');
        String webpStr = localFile.substring(0, dotIndex) + ".webp";
        return webpStr;
    }
}
