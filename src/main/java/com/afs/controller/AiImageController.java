package com.afs.controller;

import com.afs.agent.AiImageGenerator;
import com.afs.controller.filter.NoAuth;
import com.afs.service.HttpService;
import com.afs.service.RefreshTokenService;
import com.afs.utils.Coder;
import com.afs.utils.ImageUtils;
import com.afs.utils.JsonUtils;
import com.afs.utils.ResponseUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/cms")
public class AiImageController {
    private static final String uploadUrl = System.getenv().getOrDefault("AFS_MEDIA_UPLOAD_URL", "");
    private static final String publicBase = System.getenv().getOrDefault("AFS_MEDIA_PUBLIC_BASE_URL", "");
    private static final String imageApi = System.getenv().getOrDefault("AFS_VERTEX_IMAGE_URL", "");


    @Autowired
    HttpService httpService;
    @Autowired
    RefreshTokenService refreshTokenService;
    @Autowired(required = false)
    AiImageGenerator aiImageGenerator;

    @Data
    public static class CreateImageBody {
        @NotBlank
        public String desc;
    }

    @NoAuth
    @PostMapping("/ai/image/create")
    public ResponseUtils.Result createImage(@RequestBody @Valid CreateImageBody body) {
        if (aiImageGenerator != null) {
            try {
                return ResponseUtils.result(Map.of("url", aiImageGenerator.generate(body.getDesc())));
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                return ResponseUtils.error(-1, "AI 生图请求已中断");
            } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
                return ResponseUtils.error(-1, "AI 平台未返回可识别的图片响应");
            } catch (IOException failure) {
                return ResponseUtils.error(-1, failure.getMessage());
            }
        }
        Map<String, Object> resultMap = new HashMap<>();
        String url;
        try {
            url = createImg(body.getDesc());
            resultMap.put("url", url);
            return ResponseUtils.result(resultMap);
        } catch (Exception exception) {
            log.error("createImage error", exception);
            String message = exception.getMessage();
            return ResponseUtils.error(-1, message == null || message.isBlank() ? exception.toString() : message);
        }
    }

    private String createImg(String pt) throws IOException {
        if (imageApi.isBlank() || uploadUrl.isBlank() || publicBase.isBlank()) {
            throw new IOException("External image endpoints are not configured");
        }
        String jsonPt = "{\n" +
                "    \"instances\": [\n" +
                "        {\n" +
                "            \"prompt\": \"" + pt + "\"\n" +
                "        }\n" +
                "    ],\n" +
                "    \"parameters\": {\n" +
                "        \"sampleCount\": 1,\n" +
                "        \"aspectRatio\": \"16:9\",\n" +
                "        \"outputOptions\": {\n" +
                "            \"mimeType\": \"image/jpeg\",\n" +
                "            \"compressionQuality\": 30\n" +
                "        }\n" +
                "    }\n" +
                "}";
        String response = httpService.postGoogleImg(jsonPt, imageApi,
                refreshTokenService.getToken());
        Map map = JsonUtils.toMap(response);
        List<Map> list = (List<Map>) map.get("predictions");
        Map mapItem = list.get(0);
        String base64 = (String) mapItem.get("bytesBase64Encoded");

        byte[] imageBytes = Base64.getDecoder().decode(base64);
        String md5 = Coder.from(imageBytes).md5().asHexLower();
        InputStream inputStream = new ByteArrayInputStream(imageBytes);
        BufferedImage bufferedImage = ImageIO.read(inputStream); // 读取图像
        String fileName = md5 + "_gb.jpeg";
        String webFileName = ImageUtils.replaceSuffix(fileName);
        String localFilePath = "/home/ecs-user/work/temp/img/" + fileName;
        String webpFilePath = "/home/ecs-user/work/temp/img/" + webFileName;
        ImageIO.write(bufferedImage, "jpeg", Files.newOutputStream(Paths.get(localFilePath))); // 将图像保存为文件
        ImageUtils.toWebp(localFilePath, webpFilePath);
        FileSystemResource fileSystemResource = new FileSystemResource(webpFilePath);
        String path = "img/afs/";
        httpService.postImg(fileSystemResource, path, uploadUrl);
        new File(localFilePath).delete();
        new File(webpFilePath).delete();
        return publicBase.replaceFirst("/+$", "") + "/" + path + webFileName;
    }
}
