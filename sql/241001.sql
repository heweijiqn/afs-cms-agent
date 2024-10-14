ALTER TABLE article_info ADD `state` char(12) NOT NULL DEFAULT '';
ALTER TABLE article_info ADD `partner` varchar(48) NOT NULL DEFAULT '';
ALTER TABLE user ADD `partner` varchar(48) NOT NULL DEFAULT '';

CREATE TABLE `ad_resource` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `ad_resource_id` char(128) NOT NULL DEFAULT '',
  `article_id` char(128) NOT NULL DEFAULT '',
  `channel_id` char(128) NOT NULL DEFAULT '',
  `campaign` varchar(128) NOT NULL DEFAULT '',
  `land_page_url` varchar(512) NOT NULL DEFAULT '',
  `ad_platform` varchar(512) NOT NULL DEFAULT '',
  `ad_desc` varchar(2048) NOT NULL DEFAULT '',
  `audit_msg` varchar(2048) NOT NULL DEFAULT '',
  `media_list` varchar(4096) NOT NULL DEFAULT '',
  `state` char(12) NOT NULL DEFAULT '',
  `partner` varchar(48) NOT NULL DEFAULT '',
  `status` int(11) NOT NULL DEFAULT '1',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_adResourceId` (`ad_resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE adsense_data ADD `cost_per_click` double NOT NULL DEFAULT '0.0';
ALTER TABLE adsense_data ADD `impressions` double NOT NULL DEFAULT '0.0';
ALTER TABLE adsense_data ADD `ad_requests` double NOT NULL DEFAULT '0.0';
ALTER TABLE adsense_data ADD `funnel_impressions` double NOT NULL DEFAULT '0.0';
ALTER TABLE adsense_data ADD `funnel_clicks` double NOT NULL DEFAULT '0.0';
ALTER TABLE adsense_data ADD `funnel_rpm` double NOT NULL DEFAULT '0.0';
ALTER TABLE adsense_data ADD `clicks` double NOT NULL DEFAULT '0.0';
ALTER TABLE adsense_data ADD `funnel_requests` double NOT NULL DEFAULT '0.0';
ALTER TABLE adsense_data ADD `matched_ad_requests` double NOT NULL DEFAULT '0.0';
ALTER TABLE adsense_data ADD `impressions_ctr` double NOT NULL DEFAULT '0.0';
ALTER TABLE adsense_data ADD `estimated_earnings` double NOT NULL DEFAULT '0.0';
ALTER TABLE adsense_data ADD `individual_ad_impressions` double NOT NULL DEFAULT '0.0';

ALTER TABLE adsense_data ADD `tt_clicks` double NOT NULL DEFAULT '0.0';
ALTER TABLE adsense_data ADD `tt_spend` double NOT NULL DEFAULT '0.0';
ALTER TABLE adsense_data ADD `tt_impressions` double NOT NULL DEFAULT '0.0';
ALTER TABLE adsense_data ADD `tt_conversion` double NOT NULL DEFAULT '0.0';

ALTER TABLE adsense_data ADD `tb_clicks` double NOT NULL DEFAULT '0.0';
ALTER TABLE adsense_data ADD `tb_spend` double NOT NULL DEFAULT '0.0';
ALTER TABLE adsense_data ADD `tb_impressions` double NOT NULL DEFAULT '0.0';
ALTER TABLE adsense_data ADD `tb_conversion` double NOT NULL DEFAULT '0.0';