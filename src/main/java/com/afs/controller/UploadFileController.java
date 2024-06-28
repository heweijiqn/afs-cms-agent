package com.afs.controller;

import com.afs.controller.filter.NoAuth;
import com.afs.service.HttpService;
import com.afs.service.LocalMediaStore;
import com.afs.utils.Coder;
import com.afs.utils.ImageUtils;
import com.afs.utils.ResponseUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/cms")
public class UploadFileController {
    private static final String uploadUrl = System.getenv().getOrDefault("AFS_MEDIA_UPLOAD_URL", "");
    private static final String publicBase = System.getenv().getOrDefault("AFS_MEDIA_PUBLIC_BASE_URL", "");


    @Autowired
    HttpService httpService;
    @Autowired(required = false)
    LocalMediaStore localMedia;

    @ResponseBody
    @PostMapping("/upload/image")
    public Object uploadImg(@RequestParam("file") MultipartFile file) throws IOException {
        if (localMedia != null) return ResponseUtils.result(localMedia.save(file, true));
        if (uploadUrl.isBlank() || publicBase.isBlank()) return ResponseUtils.error(-1, "媒体连接器未配置");
        try {
            String md5 = Coder.from(file.getBytes()).md5().asHexLower();
            String fileName = md5 + "_" + file.getOriginalFilename();
            String localFile = "/home/ecs-user/work/temp/img/" + fileName;
            String url = uploadUrl;
            String path = "img/afs/";
            writeToFeedFile(localFile, file);
            String webpFile = replaceSuffix(localFile);
            ImageUtils.toWebp(localFile, webpFile);
            httpService.postImg(new FileSystemResource(new File(webpFile).exists() ? webpFile : localFile), path, url);
            File wFile = new File(webpFile);
            wFile.delete();
            File dFile = new File(localFile);
            dFile.delete();
            Map<String, String> map = new HashMap<>();
            map.put("name", file.getOriginalFilename());
            map.put("url", publicBase.replaceFirst("/+$", "") + "/" + path + replaceSuffix(fileName));
            return ResponseUtils.result(map);
        } catch (Exception e) {
            log.error("error to uploadFile", e);
            String message = e.getMessage();
            return ResponseUtils.error(-1, message == null || message.isBlank() ? e.toString() : message);
        }
    }

    private static String replaceSuffix(String localFile) {
        int dotIndex = localFile.lastIndexOf('.');
        String webpStr = localFile.substring(0, dotIndex) + ".webp";
        return webpStr;
    }

    static void writeToFeedFile(String path, MultipartFile multipartFile) throws Exception {
        InputStream inputStream = multipartFile.getInputStream();
        File file;

        FileOutputStream fos = null;
        try {
            file = new File(path);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            fos = new FileOutputStream(file);
            IOUtils.copy(inputStream, fos);
        } finally {
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(fos);
        }
    }
}
