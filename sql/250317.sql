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
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_metaDateCountryCampaign` (`date`,`country_code`,`campaign_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `google_data` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `date` varchar(18) NOT NULL DEFAULT '',
  `country_id` varchar(18) NOT NULL DEFAULT '',
  `country_code` varchar(12) NOT NULL DEFAULT '',
  `campaign_id` varchar(56) NOT NULL DEFAULT '',
  `campaign_name` varchar(128) NOT NULL DEFAULT '',
  `landing_url` varchar(1024) DEFAULT NULL,
  `channel_id` varchar(18) NOT NULL DEFAULT '',
  `clicks` double NOT NULL DEFAULT '0',
  `spend` double NOT NULL DEFAULT '0',
  `impressions` double NOT NULL DEFAULT '0',
  `conversion` double NOT NULL DEFAULT '0',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `advertiser` varchar(56) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_googleDateCountryCampaign` (`date`,`country_code`,`campaign_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;