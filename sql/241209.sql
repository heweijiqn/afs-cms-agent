ALTER TABLE crawl_content ADD `category` varchar(48) NOT NULL DEFAULT '';

ALTER TABLE ad_account_info ADD `user_name` varchar(128) NOT NULL DEFAULT '';
ALTER TABLE ad_account_info ADD `partner` varchar(128) NOT NULL DEFAULT '';

ALTER TABLE taboola_data ADD `account_name` varchar(128) NOT NULL DEFAULT '';