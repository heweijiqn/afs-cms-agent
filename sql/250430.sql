CREATE TABLE `ad_resource_rule` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL DEFAULT '',
  `creator` varchar(24) NOT NULL DEFAULT '',
  `type` varchar(12) NOT NULL DEFAULT '',
  `action_cycle` varchar(24) NOT NULL DEFAULT '',
  `action_time` int(11) NOT NULL DEFAULT '1',
  `rule` mediumtext NOT NULL,
  `budget_change` double NOT NULL DEFAULT '0',
  `status` int(11) NOT NULL DEFAULT '1',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ad_resource_rule_log` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `ad_resource_id` varchar(128) NOT NULL DEFAULT '',
  `ad_group_id` varchar(128) NOT NULL DEFAULT '',
  `ad_group_name` varchar(256) NOT NULL DEFAULT '',
  `campaign_id` varchar(128) NOT NULL DEFAULT '',
  `campaign_name` varchar(256) NOT NULL DEFAULT '',
  `rule_id` bigint(20) unsigned NOT NULL COMMENT '',
  `rule_name` varchar(56) NOT NULL DEFAULT '',
  `action` varchar(56) NOT NULL DEFAULT '',
  `day_num` int(11) NOT NULL DEFAULT 0,
  `condition` varchar(56) NOT NULL DEFAULT '',
  `httpBody` mediumtext NOT NULL,
  `httpResponse` mediumtext NOT NULL,
  `status` int(11) NOT NULL DEFAULT '1',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `tiktok_ad_group_info` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `ad_resource_id` varchar(128) NOT NULL DEFAULT '',
  `channel_id` varchar(128) NOT NULL DEFAULT '',
  `ad_group_id` varchar(128) NOT NULL DEFAULT '',
  `ad_group_name` varchar(256) NOT NULL DEFAULT '',
  `campaign_id` varchar(128) NOT NULL DEFAULT '',
  `campaign_name` varchar(256) NOT NULL DEFAULT '',
  `advertiser` varchar(128) NOT NULL DEFAULT '',
  `ad_group_status` varchar(128) NOT NULL DEFAULT '',
  `budget` double NOT NULL DEFAULT '0',
  `conversion` double NOT NULL DEFAULT '0',
  `gross_revenue` double NOT NULL DEFAULT '0',
  `ad_spend` double NOT NULL DEFAULT '0',
  `profit` double NOT NULL DEFAULT '0',
  `roi` double NOT NULL DEFAULT '0',
  `status` int(11) NOT NULL DEFAULT '1',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_adGroupId` (`ad_group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `ad_resource` ADD COLUMN rule_id bigint(20) unsigned COMMENT '';
ALTER TABLE `ad_resource` ADD COLUMN ad_spend  double NOT NULL DEFAULT '0' COMMENT '';
ALTER TABLE `ad_resource` ADD COLUMN gross_revenue  double NOT NULL DEFAULT '0' COMMENT '';
ALTER TABLE `ad_resource` ADD COLUMN profit  double NOT NULL DEFAULT '0' COMMENT '';
ALTER TABLE `ad_resource` ADD COLUMN roi  double NOT NULL DEFAULT '0' COMMENT '';
ALTER TABLE `ad_resource` ADD COLUMN last_action_time varchar(24) NOT NULL DEFAULT '' COMMENT '';
ALTER TABLE `ad_resource` ADD COLUMN start_rule_time varchar(24) NOT NULL DEFAULT '' COMMENT '';


CREATE TABLE `ad_text` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL DEFAULT '',
  `ad_text` mediumtext NOT NULL,
  `status` int(11) NOT NULL DEFAULT 1,
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `ad_resource` ADD COLUMN ad_platform_info mediumtext COMMENT '';
ALTER TABLE `ad_resource` ADD COLUMN ad_platform_state varchar(24) DEFAULT '' COMMENT '';
ALTER TABLE `ad_resource` ADD COLUMN ad_platform_msg mediumtext COMMENT '';

CREATE TABLE `tiktok_pixel_info` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `pixel_id` varchar(128) NOT NULL DEFAULT '',
  `pixel_code` varchar(128) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pixelId` (`pixel_id`),
  UNIQUE KEY `uk_pixelCode` (`pixel_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `google_bigquery_keyword_data` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `pd` varchar(24) NOT NULL DEFAULT '',
  `article_id` varchar(128) NOT NULL DEFAULT '',
  `key_word` varchar(512) NOT NULL DEFAULT '',
  `count` int(11) NOT NULL DEFAULT 0,
  `status` int(11) NOT NULL DEFAULT 1,
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pdAndKeyWordAndArticleId` (`pd`,`key_word`,`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `keyword_report_data` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `pd` varchar(24) NOT NULL DEFAULT '',
  `article_id` varchar(128) NOT NULL DEFAULT '',
  `keyword` varchar(512) NOT NULL DEFAULT '',
  `status` int(11) NOT NULL DEFAULT 1,
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `meta_data` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `date` varchar(128) NOT NULL DEFAULT '',
  `country_code` varchar(128) NOT NULL DEFAULT '',
  `campaign_id` varchar(256) NOT NULL DEFAULT '',
  `campaign_name` varchar(128) NOT NULL DEFAULT '',
  `landing_url` varchar(1024) DEFAULT NULL,
  `channel_id` varchar(128) NOT NULL DEFAULT '',
  `clicks` double NOT NULL DEFAULT '0',
  `spend` double NOT NULL DEFAULT '0',
  `impressions` double NOT NULL DEFAULT '0',
  `conversion` double NOT NULL DEFAULT '0',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `advertiser` varchar(128) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dateCountryCampaign` (`date`,`country_code`,`campaign_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `article_info` ADD COLUMN `online_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `adsense_data` ADD COLUMN `ad_resource_id` varchar(128) NOT NULL DEFAULT '' COMMENT '';

