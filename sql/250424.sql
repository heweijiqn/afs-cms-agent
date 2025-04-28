CREATE TABLE `ad_material` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `ad_material_id` varchar(128) NOT NULL DEFAULT '',
  `name` varchar(128) NOT NULL DEFAULT '',
  `user_name` varchar(36) NOT NULL DEFAULT '',
  `country` varchar(36) NOT NULL DEFAULT '',
  `lang` varchar(12) NOT NULL DEFAULT '',
  `type` varchar(12) NOT NULL DEFAULT '',
  `dimension` varchar(24) NOT NULL DEFAULT '',
  `ratio` varchar(24) NOT NULL DEFAULT '',
  `category` varchar(24) NOT NULL DEFAULT '',
  `template` varchar(128) NOT NULL DEFAULT '',
  `cover` varchar(1024) NOT NULL DEFAULT '',
  `url` varchar(1024) NOT NULL DEFAULT '',
  `size` int(20) NOT NULL DEFAULT '0',
  `duration` int(20) NOT NULL DEFAULT '0',
  `status` int(11) NOT NULL DEFAULT '1',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_AdMaterialId` (`ad_material_id`)
) ENGINE=InnoDB AUTO_INCREMENT = 100001 DEFAULT CHARSET=utf8mb4;

 CREATE TABLE `ad_material_category` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `value` varchar(128) NOT NULL DEFAULT '',
  `label` varchar(128) NOT NULL DEFAULT '',
    `status` int(11) NOT NULL DEFAULT '1',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `value` (`value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ad_material_category_child` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `value` varchar(128) NOT NULL DEFAULT '',
  `value_child` varchar(128) NOT NULL DEFAULT '',
  `label_child` varchar(128) NOT NULL DEFAULT '',
    `status` int(11) NOT NULL DEFAULT '1',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ukValueAndChildValue` (`value`,`value_child`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `user` ADD COLUMN show_material int(11) NOT NULL DEFAULT '0' COMMENT '';