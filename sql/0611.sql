ALTER TABLE article_info ADD COLUMN site VARCHAR(128) NOT NULL DEFAULT '' COMMENT '' AFTER category;

ALTER TABLE tiktok_data MODIFY COLUMN landing_url VARCHAR(1024);
ALTER TABLE taboola_data MODIFY COLUMN landing_url VARCHAR(1024);

-----20250329---
ALTER TABLE `user` ADD COLUMN auto_article int(11) NOT NULL DEFAULT '0' COMMENT '';
ALTER TABLE `user` ADD COLUMN auto_campaign int(11) NOT NULL DEFAULT '0' COMMENT '';
ALTER TABLE `user` ADD COLUMN site_list VARCHAR(256) NOT NULL DEFAULT '' COMMENT '';
ALTER TABLE `user` ADD COLUMN `desc` VARCHAR(256) NOT NULL DEFAULT '' COMMENT '';

----20250401---
ALTER TABLE `user` ADD COLUMN ad_account_list VARCHAR(256) NOT NULL DEFAULT '' COMMENT '';
ALTER TABLE `ad_resource` ADD COLUMN `country` VARCHAR(24) NOT NULL DEFAULT '' COMMENT '';
ALTER TABLE `ad_resource` ADD COLUMN `keyword` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '';
ALTER TABLE `ad_resource` ADD COLUMN `ad_account` VARCHAR(256) NOT NULL DEFAULT '' COMMENT '';
--
ALTER TABLE ad_resource DROP COLUMN ad_account;
--

---20250406----
CREATE TABLE `distributed_lock` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `lock_key` varchar(255) NOT NULL COMMENT '锁的名称',
  `lock_value` varchar(255) DEFAULT NULL COMMENT '锁的内容',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '锁的状态 0 空闲 1 被占用',
  `expire_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '过期时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lock_key` (`lock_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分布式锁的表';

---20250412---

ALTER TABLE `tiktok_data` ADD COLUMN `advertiser` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '';