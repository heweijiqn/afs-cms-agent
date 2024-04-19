package com.afs.controller;

import com.afs.controller.filter.NoAuth;
import com.afs.service.AliUploadFileService;
import com.afs.service.HttpService;
import com.afs.service.LocalMediaStore;
import com.afs.utils.AppUtils;
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
import java.util.UUID;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/cms/ali/")
public class AliUploadFileController {

    @Autowired
    AliUploadFileService aliUploadFileService;
    @Autowired
    HttpService httpService;
    @Autowired(required = false)
    LocalMediaStore localMedia;

    @NoAuth
    @ResponseBody
    @PostMapping("/upload/file")
    public Object uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        if (localMedia != null) return ResponseUtils.result(localMedia.save(file, false));
        try {
            Map<String, String> uploadMap = aliUploadFileService.uploadFile(file, "afs/" + AppUtils.getUUID());
            Map<String, String> map = new HashMap<>();
            map.put("name", file.getOriginalFilename());
            map.put("url", uploadMap.get("url"));
            return ResponseUtils.result(map);
        } catch (Exception e) {
            log.error("error to uploadFile", e);
            String message = e.getMessage();
            return ResponseUtils.error(-1, message == null || message.isBlank() ? e.toString() : message);
        }
    }
}
