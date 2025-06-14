CREATE TABLE IF NOT EXISTS public_ad_case_metric (
    case_id VARCHAR(64) NOT NULL,
    platform VARCHAR(40) NOT NULL,
    advertiser VARCHAR(160) NOT NULL,
    measurement_scope VARCHAR(240) NOT NULL,
    metric_code VARCHAR(48) NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    metric_value DECIMAL(20, 4) NOT NULL,
    metric_unit VARCHAR(12) NOT NULL,
    source_url VARCHAR(512) NOT NULL,
    PRIMARY KEY (case_id, metric_code)
);

-- Platform disclosures. A change_pct value of 35 means a 35% increase over the comparison group.
MERGE INTO public_ad_case_metric AS stored
USING (VALUES
    ('google-loci', 'Google Ads', 'LØCI', '2023 年整体投放', 'roas', 'ROAS', 5.0000, 'ratio',
     'https://business.google.com/uk/resources/success-stories/loci/'),
    ('tiktok-rinascimento', 'TikTok Ads', 'Rinascimento', 'Smart+ Catalog Ads 电商转化', 'roas', 'ROAS', 15.0000, 'ratio',
     'https://ads.tiktok.com/business/en-US/inspiration/rinascimento-tiktok-success-stories'),
    ('tiktok-rinascimento', 'TikTok Ads', 'Rinascimento', 'Smart+ Catalog Ads 电商转化', 'ctr', 'CTR', 0.8600, 'percent',
     'https://ads.tiktok.com/business/en-US/inspiration/rinascimento-tiktok-success-stories'),
    ('tiktok-rinascimento', 'TikTok Ads', 'Rinascimento', 'Smart+ Catalog Ads 电商转化', 'cpa', 'CPA', 8.0000, 'EUR',
     'https://ads.tiktok.com/business/en-US/inspiration/rinascimento-tiktok-success-stories'),
    ('meta-monos', 'Meta Ads', 'Monos', 'Advantage+ Shopping 对比原活动设置', 'roas_change', '增量 ROAS 变化', 35.0000, 'change_pct',
     'https://about.fb.com/news/2023/06/how-ai-is-powering-marketing-success-and-business-growth/'),
    ('meta-monos', 'Meta Ads', 'Monos', 'Advantage+ Shopping 对比原活动设置', 'cpp_change', '增量单次购买成本变化', -58.0000, 'change_pct',
     'https://about.fb.com/news/2023/06/how-ai-is-powering-marketing-success-and-business-growth/'),
    ('taboola-renault', 'Taboola', 'Renault Australia', '对比另一家原生广告服务商', 'cpa_change', 'CPA 变化', -51.0000, 'change_pct',
     'https://www.taboola.com/resources/case-studies/renault-australia'),
    ('taboola-renault', 'Taboola', 'Renault Australia', '对比另一家原生广告服务商', 'cpc_change', 'CPC 变化', -16.0000, 'change_pct',
     'https://www.taboola.com/resources/case-studies/renault-australia')
) AS disclosed(case_id, platform, advertiser, measurement_scope, metric_code, metric_name, metric_value, metric_unit, source_url)
ON stored.case_id = disclosed.case_id AND stored.metric_code = disclosed.metric_code
WHEN MATCHED THEN UPDATE SET advertiser = disclosed.advertiser,
    measurement_scope = disclosed.measurement_scope, metric_name = disclosed.metric_name
WHEN NOT MATCHED THEN INSERT
    (case_id, platform, advertiser, measurement_scope, metric_code, metric_name, metric_value, metric_unit, source_url)
VALUES (disclosed.case_id, disclosed.platform, disclosed.advertiser, disclosed.measurement_scope,
        disclosed.metric_code, disclosed.metric_name, disclosed.metric_value, disclosed.metric_unit, disclosed.source_url);
