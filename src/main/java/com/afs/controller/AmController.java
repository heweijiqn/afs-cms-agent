package com.afs.controller;

import com.afs.controller.filter.NoAuth;
import com.afs.utils.ResponseUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/cms/am")
public class AmController {

    @GetMapping("/list")
    public ResponseUtils.Result getList() {
        return ResponseUtils.result(Arrays.asList("cjh", "haven","tao", "pudge","zhaogu", "ningshi", "caoshaoshuai", "zhaotao", "caosen"));
    }
}
