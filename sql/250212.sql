CREATE TABLE `taboola_data` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `date` varchar(128) NOT NULL DEFAULT '',
  `country_code` varchar(128) NOT NULL DEFAULT '',
  `campaign_id` varchar(256) NOT NULL DEFAULT '',
  `campaign_name` varchar(128) NOT NULL DEFAULT '',
  `landing_url` varchar(128) NOT NULL DEFAULT '',
  `channel_id` varchar(128) NOT NULL DEFAULT '',
  `clicks` double NOT NULL DEFAULT '0',
  `spend` double NOT NULL DEFAULT '0',
  `impressions` double NOT NULL DEFAULT '0',
  `conversion` double NOT NULL DEFAULT '0',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_taboolaDateCountryCampaign` (`date`,`country_code`,`campaign_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;