-- Apply only to the local fixture rows. Content-ad clicks are independent
-- of the acquisition campaign's conversion events.
UPDATE adsense_data
SET clicks = CEIL(individual_ad_impressions * (0.008 + MOD(id, 19) / 1000.0)),
    funnel_clicks = CEIL(funnel_impressions * (0.008 + MOD(id, 19) / 1000.0))
WHERE ad_resource_id LIKE 'afs-campaign-%'
  AND CAST(adsense_data AS VARCHAR) = '{"synthetic":true}'
  AND NOT EXISTS (SELECT 1 FROM local_migration WHERE name = 'synthetic-report-clicks-v1');
UPDATE adsense_data
SET impressions_ctr = clicks / NULLIF(individual_ad_impressions, 0),
    cost_per_click = estimated_earnings / NULLIF(clicks, 0),
    funnel_rpm = 1000 * estimated_earnings / NULLIF(funnel_impressions, 0)
WHERE ad_resource_id LIKE 'afs-campaign-%'
  AND CAST(adsense_data AS VARCHAR) = '{"synthetic":true}'
  AND NOT EXISTS (SELECT 1 FROM local_migration WHERE name = 'synthetic-report-clicks-v1');
INSERT INTO local_migration(name)
SELECT 'synthetic-report-clicks-v1' WHERE EXISTS (
    SELECT 1 FROM adsense_data WHERE ad_resource_id LIKE 'afs-campaign-%' AND CAST(adsense_data AS VARCHAR) = '{"synthetic":true}'
) AND NOT EXISTS (SELECT 1 FROM local_migration WHERE name = 'synthetic-report-clicks-v1');
