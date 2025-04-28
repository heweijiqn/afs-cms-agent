CREATE TABLE `adsense_account` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `pub_id` varchar(128) NOT NULL DEFAULT '',
  `site` varchar(1024) NOT NULL DEFAULT '',
  `status` int(11) NOT NULL DEFAULT '1',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pubId` (`pub_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `adsense_channel` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `pub_id` varchar(128) NOT NULL DEFAULT '',
  `channel_id` varchar(128) NOT NULL DEFAULT '',
  `state` varchar(24) NOT NULL DEFAULT '',
  `status` int(11) NOT NULL DEFAULT '1',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pubIdAndChannelId` (`pub_id`,`channel_id`),
  UNIQUE KEY `uk_channelId` (`channel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;