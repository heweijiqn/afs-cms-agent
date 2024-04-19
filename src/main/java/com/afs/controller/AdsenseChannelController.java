package com.afs.controller;

import com.afs.controller.filter.NoAuth;
import com.afs.dao.AdResourceMapper;
import com.afs.dao.AdsenseAccountMapper;
import com.afs.dao.AdsenseChannelMapper;
import com.afs.dao.ArticleInfoMapper;
import com.afs.dao.model.AdResource;
import com.afs.dao.model.AdsenseAccount;
import com.afs.dao.model.AdsenseChannel;
import com.afs.utils.Page;
import com.afs.utils.ResponseUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/cms/adsense/channel")
public class AdsenseChannelController {

    @Autowired
    AdResourceMapper adResourceMapper;
    @Autowired
    ArticleInfoMapper articleInfoMapper;
    @Autowired
    AdsenseChannelMapper adsenseChannelMapper;
    @Autowired
    AdsenseAccountMapper adsenseAccountMapper;

    @Data
    public static class AddBody {
        @NotBlank
        String channelId;
        @NotBlank
        String pubId;
    }

    private AdsenseChannel toAdsenseChannel(AddBody body) {
        AdsenseChannel channel = new AdsenseChannel();
        channel.setChannelId(body.channelId);
        channel.setPubId(body.pubId);
        channel.setState(getChannelState(body.getChannelId()));
        return channel;
    }

    private String getChannelState(String channelId) {
        int n = adResourceMapper.countByChannelIdByStateList(channelId, Arrays.asList(AdResource.PASS, AdResource.AUDITING));
        if (n > 0) {
            return AdsenseChannel.ONLINE;
        } else {
            return AdsenseChannel.INIT;
        }
    }

//    @NoAuth
    @PostMapping("/add")
    public ResponseUtils.Result add(@RequestBody List<AddBody> bodyList) {
        for (AddBody body : bodyList) {
            if (adsenseChannelMapper.countByPubIdAndChannelId(body.getChannelId(), body.getPubId()) > 0) {
                return ResponseUtils.error(-1, body.getPubId() + ":" +body.getChannelId() + "channelId和pubId的对应关系已存在，请检查");
            }
            if (adsenseAccountMapper.countByPubId(body.getPubId()) <= 0) {
                return ResponseUtils.error(-1, body.getPubId() + ":" +body.getChannelId() + "pubId未添加");
            }
            if (!isValidChannel(body.getChannelId()) || !isValidPubId(body.getPubId())) {
                return ResponseUtils.error(-1, body.getPubId() + " : " +body.getChannelId() + "不规范请检查");
            }
        }
        for (AddBody body : bodyList) {
            AdsenseChannel adsenseChannel = toAdsenseChannel(body);
            adsenseChannelMapper.add(adsenseChannel);
        }
        return ResponseUtils.ok();
    }

    private static boolean isValidPubId(String pubId) {
        if (!pubId.contains("pub-")) {
            return false;
        }
        String t = pubId.replace("pub-", "");
        return t.matches("^\\d{16}$");
    }

    private static boolean isValidChannel(String channelId) {
        return channelId != null && channelId.matches("^\\d{10}$");
    }

    @Data
    public static class DelBody {
        @NotBlank
        public String channelId;
        @NotBlank
        public String pubId;
    }

//    @NoAuth
    @PostMapping("/delete")
    public ResponseUtils.Result deleteChannel(@RequestBody @Valid DelBody body) {
        if (adResourceMapper.countByChannelIdByStateList(body.getChannelId(), Arrays.asList(AdResource.AUDITING, AdResource.PASS)) > 0) {
            return ResponseUtils.error(-1, "channelId有Campaign在使用，请先处理");
        }
        int n = adsenseChannelMapper.del(body.getChannelId(), body.getPubId());
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "更新失败");
        }
    }

    @Data
    public static class AdsenseChannelVO {
        private String channelId;
        private String pubId;
        private String state;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        public Date ctime;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        public Date utime;
    }

    private AdsenseChannelVO toAdsenseChannelVO(AdsenseChannel adsenseChannel) {
        AdsenseChannelVO adsenseChannelVO = new AdsenseChannelVO();
        adsenseChannelVO.setChannelId(adsenseChannel.getChannelId());
        adsenseChannelVO.setPubId(adsenseChannel.getPubId());
        adsenseChannelVO.setState(adsenseChannel.getState());
        adsenseChannelVO.setUtime(adsenseChannel.getUtime());
        adsenseChannelVO.setCtime(adsenseChannel.getCtime());
        return adsenseChannelVO;
    }

    @Data
    public static class ListRequest {
        public String pubId;
        public String channelId;
        public String state;
        @NotNull
        public Integer pageSize;
        @NotNull
        public Integer pageIndex;
    }

//    @NoAuth
    @PostMapping("/list")
    public ResponseUtils.Result getList(@RequestBody @Valid ListRequest request) {
        Page page = new Page();
        page.size = Math.max(10, request.getPageSize());
        page.index = request.getPageIndex();
        int offset = Math.max(0, (request.getPageIndex() - 1)) * request.getPageSize();
        List<AdsenseChannel> adsenseChannelList = adsenseChannelMapper.getListByQuery(request.getState(), request.getPubId(), request.getChannelId(),
                offset, request.getPageSize());
        page.list = adsenseChannelList.stream().map(e -> toAdsenseChannelVO(e)).collect(Collectors.toList());
        page.totalSize = adsenseChannelMapper.countByQuery(request.getState(), request.getPubId(), request.getChannelId());
        page.maxIndex = page.totalSize / request.getPageSize();
        return ResponseUtils.result(page);
    }
}
