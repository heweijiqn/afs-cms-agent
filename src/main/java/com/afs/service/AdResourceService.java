package com.afs.service;

import com.afs.dao.AdsenseAccountMapper;
import com.afs.dao.model.AdsenseAccount;
import com.afs.utils.AppUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AdResourceService {

    @Autowired
    AdsenseAccountMapper adsenseAccountMapper;

    public String getCusChannelId(String adResourceId, String channelId, String landPageUrl) {
        if (StringUtils.isBlank(channelId) || StringUtils.isBlank(landPageUrl)) {
            return "";
        }
        String site = null;
        try {
            site = AppUtils.getSite(landPageUrl);
        } catch (Exception e) {
            log.error("error to getCusChannelId, adResourceId={}, channelId={}, landPageUrl={}", adResourceId, channelId, landPageUrl);
        }
        AdsenseAccount adsenseAccount = adsenseAccountMapper.getBySite(site);
        if (adsenseAccount == null) {
            log.error("getCusChannelId site={}, land={}, adsenseAccount={}", site, landPageUrl, adsenseAccount);
            return channelId;
        }
        return "partner-" + adsenseAccount.getPubId() + ":" + channelId;
    }

    public String getCusChannelId(String channelId, String landPageUrl) {
        if (StringUtils.isBlank(channelId) || StringUtils.isBlank(landPageUrl)) {
            return "";
        }
        String site = null;
        try {
            site = AppUtils.getSite(landPageUrl);
        } catch (Exception e) {
            log.error("error to getCusChannelId channelId={}, landPageUrl={}", channelId, landPageUrl);
        }
        AdsenseAccount adsenseAccount = adsenseAccountMapper.getBySite(site);
        if (adsenseAccount == null) {
            log.error("getCusChannelId site={}, land={}, adsenseAccount={}", site, landPageUrl, adsenseAccount);
            return channelId;
        }
        return "partner-" + adsenseAccount.getPubId() + ":" + channelId;
    }

}
