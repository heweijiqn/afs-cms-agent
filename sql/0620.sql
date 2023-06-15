CREATE TABLE `lang` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `value` varchar(128) NOT NULL DEFAULT '',
  `label` varchar(128) NOT NULL DEFAULT '' COMMENT '',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `value` (`value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `category` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `value` varchar(128) NOT NULL DEFAULT '',
  `label` varchar(128) NOT NULL DEFAULT '' COMMENT '',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `value` (`value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `config_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `value` mediumtext NOT NULL,
  `desc` varchar(256) NOT NULL,
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `op` varchar(12) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `nameUk` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4

CREATE TABLE `feedback` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `site` varchar(64) NOT NULL DEFAULT '',
  `locale` varchar(64) NOT NULL DEFAULT '',
  `rate` int(11) NOT NULL DEFAULT '1',
  `value` mediumtext NOT NULL,
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;