-- Shared H2 business tables for the original CMS and API controllers.

CREATE TABLE IF NOT EXISTS article_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, article_id VARCHAR(128), title VARCHAR(1024), terms VARCHAR(1024),
    category VARCHAR(256), strategy VARCHAR(128), img_url VARCHAR(2048), first_part CLOB, text_list CLOB,
    lang VARCHAR(32), state VARCHAR(64), other_info CLOB, online INT DEFAULT 1, inside_show INT DEFAULT 1,
    status INT DEFAULT 1, site VARCHAR(256), ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS config_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(256), `value` CLOB, `desc` CLOB, site VARCHAR(256),
    ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP, utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, `value` VARCHAR(256), label VARCHAR(256), site VARCHAR(256),
    ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP, utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS report_data_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, channel VARCHAR(128), `value` CLOB, `desc` CLOB, site VARCHAR(256),
    ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP, utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS ad_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, app_id VARCHAR(128), ad_id VARCHAR(128), day_time BIGINT,
    country VARCHAR(64), pv DOUBLE, total DOUBLE, ecpm DOUBLE, page_site VARCHAR(256), page_pv DOUBLE,
    click DOUBLE, page_ecpm DOUBLE, other CLOB, status INT DEFAULT 1, ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS app_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, app_id VARCHAR(128), pname VARCHAR(256), name VARCHAR(256), os VARCHAR(64),
    ratio DOUBLE, app_secret VARCHAR(512), app_store_url VARCHAR(2048), is_on_app_store INT, status INT DEFAULT 1,
    ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP, utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS ad_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, ad_id VARCHAR(128), app_id VARCHAR(128), name VARCHAR(256),
    ratio DOUBLE, type INT, on_off INT, status INT DEFAULT 1, ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, site VARCHAR(256), rate VARCHAR(32), locale VARCHAR(32), `value` CLOB,
    ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP, utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS ip_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, ip VARCHAR(64), site VARCHAR(256), status INT DEFAULT 1,
    ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP, utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE (ip, site)
);
CREATE TABLE IF NOT EXISTS keyword_report_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, pd VARCHAR(32), article_id VARCHAR(128), keyword VARCHAR(512),
    status INT DEFAULT 1, ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP, utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(256) UNIQUE NOT NULL,
    password VARCHAR(256) NOT NULL, partner VARCHAR(128) DEFAULT '', root INT DEFAULT 0,
    status INT DEFAULT 1, `desc` VARCHAR(512) DEFAULT '', auto_article BOOLEAN DEFAULT FALSE,
    auto_campaign BOOLEAN DEFAULT FALSE, show_material BOOLEAN DEFAULT FALSE,
    site_list CLOB, ad_account_list CLOB, ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS user_access (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, user_name VARCHAR(256), app_id VARCHAR(128),
    access INT, ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP, utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_name, app_id)
);
CREATE TABLE IF NOT EXISTS category_child (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, `value` VARCHAR(256), value_child VARCHAR(256),
    label_child VARCHAR(256), site VARCHAR(256), ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE(site, `value`, value_child)
);
CREATE TABLE IF NOT EXISTS author_category_map (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, `value` VARCHAR(256), label VARCHAR(256),
    author VARCHAR(256), category VARCHAR(256), site VARCHAR(256), ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS ad_account_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(256), user_name VARCHAR(256), partner VARCHAR(256),
    account_id VARCHAR(128), ad_platform VARCHAR(128) NOT NULL, info CLOB, status INT DEFAULT 1,
    ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP, utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS crawl_content (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, fid VARCHAR(128) UNIQUE, content CLOB,
    source VARCHAR(128), category VARCHAR(256), online INT DEFAULT 1, status INT DEFAULT 1,
    ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP, utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS adsense_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, `date` VARCHAR(24), product_code VARCHAR(64), country_code VARCHAR(64),
    custom_channel_id VARCHAR(128), adsense_data CLOB, status INT DEFAULT 1,
    ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP, utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE(`date`, product_code, country_code, custom_channel_id)
);
CREATE TABLE IF NOT EXISTS tiktok_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, `date` VARCHAR(24), country_code VARCHAR(64),
    campaign_id VARCHAR(128), campaign_name VARCHAR(256), landing_url VARCHAR(1024), channel_id VARCHAR(128) DEFAULT '',
    clicks DOUBLE DEFAULT 0, spend DOUBLE DEFAULT 0, impressions DOUBLE DEFAULT 0, conversion DOUBLE DEFAULT 0,
    ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP, utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE(`date`, country_code, campaign_id)
);
CREATE TABLE IF NOT EXISTS local_migration (name VARCHAR(80) PRIMARY KEY);

CREATE TABLE IF NOT EXISTS lang (
  `id` bigint  NOT NULL AUTO_INCREMENT,
  `value` varchar(128) NOT NULL DEFAULT '',
  `label` varchar(128) NOT NULL DEFAULT '',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`value`)

);

CREATE TABLE IF NOT EXISTS ad_resource (
  `id` bigint  NOT NULL AUTO_INCREMENT,
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
  `status` int NOT NULL DEFAULT '1',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`ad_resource_id`)

);

CREATE TABLE IF NOT EXISTS taboola_data (
  `id` bigint  NOT NULL AUTO_INCREMENT,
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
  UNIQUE (`date`,`country_code`,`campaign_id`)

);

CREATE TABLE IF NOT EXISTS meta_data (
  `id` bigint  NOT NULL AUTO_INCREMENT,
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
  UNIQUE (`date`,`country_code`,`campaign_id`)

);

CREATE TABLE IF NOT EXISTS google_data (
  `id` bigint  NOT NULL AUTO_INCREMENT,
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
  UNIQUE (`date`,`country_code`,`campaign_id`)

);

CREATE TABLE IF NOT EXISTS adsense_account (
  `id` bigint  NOT NULL AUTO_INCREMENT,
  `pub_id` varchar(128) NOT NULL DEFAULT '',
  `site` varchar(1024) NOT NULL DEFAULT '',
  `status` int NOT NULL DEFAULT '1',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`pub_id`)

);

CREATE TABLE IF NOT EXISTS adsense_channel (
  `id` bigint  NOT NULL AUTO_INCREMENT,
  `pub_id` varchar(128) NOT NULL DEFAULT '',
  `channel_id` varchar(128) NOT NULL DEFAULT '',
  `state` varchar(24) NOT NULL DEFAULT '',
  `status` int NOT NULL DEFAULT '1',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`pub_id`,`channel_id`),
  UNIQUE (`channel_id`)

);

CREATE TABLE IF NOT EXISTS ad_material (
  `id` bigint  NOT NULL AUTO_INCREMENT,
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
  `size` int NOT NULL DEFAULT '0',
  `duration` int NOT NULL DEFAULT '0',
  `status` int NOT NULL DEFAULT '1',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`ad_material_id`)

);

CREATE TABLE IF NOT EXISTS ad_material_category (
  `id` bigint  NOT NULL AUTO_INCREMENT,
  `value` varchar(128) NOT NULL DEFAULT '',
  `label` varchar(128) NOT NULL DEFAULT '',
    `status` int NOT NULL DEFAULT '1',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`value`)

);

CREATE TABLE IF NOT EXISTS ad_material_category_child (
  `id` bigint  NOT NULL AUTO_INCREMENT,
  `value` varchar(128) NOT NULL DEFAULT '',
  `value_child` varchar(128) NOT NULL DEFAULT '',
  `label_child` varchar(128) NOT NULL DEFAULT '',
    `status` int NOT NULL DEFAULT '1',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`value`,`value_child`)

);

CREATE TABLE IF NOT EXISTS ad_resource_rule (
  `id` bigint  NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL DEFAULT '',
  `creator` varchar(24) NOT NULL DEFAULT '',
  `type` varchar(12) NOT NULL DEFAULT '',
  `action_cycle` varchar(24) NOT NULL DEFAULT '',
  `action_time` int NOT NULL DEFAULT '1',
  `rule` mediumtext NOT NULL,
  `budget_change` double NOT NULL DEFAULT '0',
  `status` int NOT NULL DEFAULT '1',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`name`)

);

CREATE TABLE IF NOT EXISTS ad_resource_rule_log (
  `id` bigint  NOT NULL AUTO_INCREMENT,
  `ad_resource_id` varchar(128) NOT NULL DEFAULT '',
  `ad_group_id` varchar(128) NOT NULL DEFAULT '',
  `ad_group_name` varchar(256) NOT NULL DEFAULT '',
  `campaign_id` varchar(128) NOT NULL DEFAULT '',
  `campaign_name` varchar(256) NOT NULL DEFAULT '',
  `rule_id` bigint  NOT NULL,
  `rule_name` varchar(56) NOT NULL DEFAULT '',
  `action` varchar(56) NOT NULL DEFAULT '',
  `day_num` int NOT NULL DEFAULT 0,
  `condition` varchar(56) NOT NULL DEFAULT '',
  `httpBody` mediumtext NOT NULL,
  `httpResponse` mediumtext NOT NULL,
  `status` int NOT NULL DEFAULT '1',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)

);

CREATE TABLE IF NOT EXISTS tiktok_ad_group_info (
  `id` bigint  NOT NULL AUTO_INCREMENT,
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
  `status` int NOT NULL DEFAULT '1',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`ad_group_id`)

);

CREATE TABLE IF NOT EXISTS ad_text (
  `id` bigint  NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL DEFAULT '',
  `ad_text` mediumtext NOT NULL,
  `status` int NOT NULL DEFAULT 1,
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`name`)

);

CREATE TABLE IF NOT EXISTS tiktok_pixel_info (
  `id` bigint  NOT NULL AUTO_INCREMENT,
  `pixel_id` varchar(128) NOT NULL DEFAULT '',
  `pixel_code` varchar(128) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE (`pixel_id`),
  UNIQUE (`pixel_code`)

);

CREATE TABLE IF NOT EXISTS google_bigquery_keyword_data (
  `id` bigint  NOT NULL AUTO_INCREMENT,
  `pd` varchar(24) NOT NULL DEFAULT '',
  `article_id` varchar(128) NOT NULL DEFAULT '',
  `key_word` varchar(512) NOT NULL DEFAULT '',
  `count` int NOT NULL DEFAULT 0,
  `status` int NOT NULL DEFAULT 1,
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`pd`,`key_word`,`article_id`)

);

CREATE TABLE IF NOT EXISTS distributed_lock (
  `id` bigint  NOT NULL AUTO_INCREMENT,
  `lock_key` varchar(255) NOT NULL,
  `lock_value` varchar(255) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '0',
  `expire_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`lock_key`)

);

ALTER TABLE `article_info` ADD COLUMN IF NOT EXISTS `state` char(12) NOT NULL DEFAULT '';
ALTER TABLE `article_info` ADD COLUMN IF NOT EXISTS `partner` varchar(48) NOT NULL DEFAULT '';
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `partner` varchar(48) NOT NULL DEFAULT '';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `cost_per_click` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `impressions` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `ad_requests` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `funnel_impressions` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `funnel_clicks` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `funnel_rpm` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `clicks` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `funnel_requests` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `matched_ad_requests` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `impressions_ctr` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `estimated_earnings` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `individual_ad_impressions` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `tt_clicks` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `tt_spend` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `tt_impressions` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `tt_conversion` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `tb_clicks` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `tb_spend` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `tb_impressions` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `tb_conversion` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `gg_clicks` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `gg_spend` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `gg_impressions` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `gg_conversion` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `fb_clicks` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `fb_spend` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `fb_impressions` double NOT NULL DEFAULT '0.0';
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `fb_conversion` double NOT NULL DEFAULT '0.0';
ALTER TABLE `ad_resource` ADD COLUMN IF NOT EXISTS rule_id bigint;
ALTER TABLE `ad_resource` ADD COLUMN IF NOT EXISTS ad_spend double NOT NULL DEFAULT '0';
ALTER TABLE `ad_resource` ADD COLUMN IF NOT EXISTS gross_revenue double NOT NULL DEFAULT '0';
ALTER TABLE `ad_resource` ADD COLUMN IF NOT EXISTS profit double NOT NULL DEFAULT '0';
ALTER TABLE `ad_resource` ADD COLUMN IF NOT EXISTS roi double NOT NULL DEFAULT '0';
ALTER TABLE `ad_resource` ADD COLUMN IF NOT EXISTS last_action_time varchar(24) NOT NULL DEFAULT '';
ALTER TABLE `ad_resource` ADD COLUMN IF NOT EXISTS start_rule_time varchar(24) NOT NULL DEFAULT '';
ALTER TABLE `ad_resource` ADD COLUMN IF NOT EXISTS ad_platform_info mediumtext;
ALTER TABLE `ad_resource` ADD COLUMN IF NOT EXISTS ad_platform_state varchar(24) DEFAULT '';
ALTER TABLE `ad_resource` ADD COLUMN IF NOT EXISTS ad_platform_msg mediumtext;
ALTER TABLE `article_info` ADD COLUMN IF NOT EXISTS `online_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE `adsense_data` ADD COLUMN IF NOT EXISTS `ad_resource_id` varchar(128) NOT NULL DEFAULT '';
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS show_material int NOT NULL DEFAULT '0';
ALTER TABLE `article_info` ADD COLUMN IF NOT EXISTS site VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS auto_article int NOT NULL DEFAULT '0';
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS auto_campaign int NOT NULL DEFAULT '0';
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS site_list VARCHAR(256) NOT NULL DEFAULT '';
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `desc` VARCHAR(256) NOT NULL DEFAULT '';
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS ad_account_list VARCHAR(256) NOT NULL DEFAULT '';
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS display_name VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS email VARCHAR(256) NOT NULL DEFAULT '';
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS phone VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS department VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS job_title VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai';
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS avatar_color VARCHAR(32) NOT NULL DEFAULT 'teal';
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP NULL;
ALTER TABLE `ad_resource` ADD COLUMN IF NOT EXISTS `country` VARCHAR(24) NOT NULL DEFAULT '';
ALTER TABLE `ad_resource` ADD COLUMN IF NOT EXISTS `keyword` VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE `ad_resource` ADD COLUMN IF NOT EXISTS `ad_account` VARCHAR(256) NOT NULL DEFAULT '';
ALTER TABLE `tiktok_data` ADD COLUMN IF NOT EXISTS `advertiser` VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE `crawl_content` ADD COLUMN IF NOT EXISTS `category` varchar(48) NOT NULL DEFAULT '';
ALTER TABLE `ad_account_info` ADD COLUMN IF NOT EXISTS `user_name` varchar(128) NOT NULL DEFAULT '';
ALTER TABLE `ad_account_info` ADD COLUMN IF NOT EXISTS `partner` varchar(128) NOT NULL DEFAULT '';
ALTER TABLE `taboola_data` ADD COLUMN IF NOT EXISTS `account_name` varchar(128) NOT NULL DEFAULT '';
ALTER TABLE ad_resource ADD COLUMN IF NOT EXISTS account_id VARCHAR(128) DEFAULT '';
ALTER TABLE ad_resource ADD COLUMN IF NOT EXISTS title VARCHAR(1024) DEFAULT '';
ALTER TABLE ad_resource ADD COLUMN IF NOT EXISTS am VARCHAR(256) DEFAULT '';
ALTER TABLE lang ADD COLUMN IF NOT EXISTS site VARCHAR(256);
ALTER TABLE ad_material_category ADD COLUMN IF NOT EXISTS site VARCHAR(256);
ALTER TABLE google_data ADD COLUMN IF NOT EXISTS account_name VARCHAR(128);
ALTER TABLE meta_data ADD COLUMN IF NOT EXISTS advertiser VARCHAR(128);
CREATE UNIQUE INDEX IF NOT EXISTS article_info_article_id ON article_info(article_id);
CREATE UNIQUE INDEX IF NOT EXISTS category_site_value ON category(site, `value`);
CREATE UNIQUE INDEX IF NOT EXISTS app_info_app_id ON app_info(app_id);
ALTER TABLE `article_info` ADD COLUMN IF NOT EXISTS auditor VARCHAR(64) DEFAULT '';
ALTER TABLE `article_info` ADD COLUMN IF NOT EXISTS audit_time TIMESTAMP;
ALTER TABLE `ad_resource` ADD COLUMN IF NOT EXISTS auditor VARCHAR(64) DEFAULT '';
ALTER TABLE `ad_resource` ADD COLUMN IF NOT EXISTS audit_time TIMESTAMP;
