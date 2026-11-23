package com.afs.controller;

import com.afs.utils.ResponseUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/cms/site")
public class SiteController {
    @GetMapping("/list")
    public ResponseUtils.Result getList() {
        return ResponseUtils.result(sites());
    }

    public static List<String> sites() {
        List<String> list = new ArrayList<>();
        list.add("cccooperate");
        list.add("niceranker");
        list.add("valibooks");
        list.add("superraranker");
        list.add("askweai");
        list.add("bestreplyai");
        list.add("infozoneai");
        list.add("magictechai");
        list.add("vvtechai");
        list.add("wiseinfoai");
        list.add("buildmindai");
        list.add("bitsxspace");
        list.add("vitacave");
        list.add("bricktowns");
        list.add("businserve");
        list.add("cureequip");
        list.add("roomgic");
        list.add("widejd");
        list.add("fitaxon");
        list.add("windyinvest");
        list.add("nearby-dental-implants");
        list.add("beauty-center-nearby");
        list.add("insurance-consulting-nearby");
        list.add("wisewaysdaily");
        list.add("goldenlivingguide");
        list.add("searcheage");
        list.add("smileclinicguide");
        list.add("homeguarddeals");
        list.add("mentalsupporthub");
        list.add("homfixer");
        list.add("gooeco");
        list.add("getburnzy");
        return list;
    }
}
