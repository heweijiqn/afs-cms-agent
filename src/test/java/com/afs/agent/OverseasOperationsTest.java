package com.afs.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(OverseasOperationsTest.Config.class)
@org.springframework.test.context.ActiveProfiles("overseas-unit-test")
class OverseasOperationsTest {
    @org.springframework.boot.test.context.TestConfiguration @EnableTransactionManagement
    @Profile("overseas-unit-test")
    static class Config {
        @Bean DataSource source(){return new DriverManagerDataSource("jdbc:h2:mem:overseas_truth;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE","sa","");}
        @Bean JdbcTemplate db(DataSource s){new ResourceDatabasePopulator(new ClassPathResource("agent/schema.sql")).execute(s);return new JdbcTemplate(s);}
        @Bean org.springframework.transaction.PlatformTransactionManager transactionManager(DataSource s){return new DataSourceTransactionManager(s);}
        @Bean ObjectMapper json(){return new ObjectMapper();}
        @Bean OverseasDataService observations(JdbcTemplate db,ObjectMapper json){return new OverseasDataService(db,json);}
        @Bean OverseasEvidenceService evidence(JdbcTemplate db,OverseasDataService fx){return new OverseasEvidenceService(db,fx);}
    }
    @Autowired OverseasDataService data;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired OverseasEvidenceService evidence;
    CmsCampaignDataService cms;
    AdOverseasOpsService service;
    @BeforeEach void reset(){
        for(String t:List.of("ad_cohort_observation","ad_fx_rate","ad_automation_claim","ad_smart_automation_rule","ad_automation_execution_log","local_api_setting","ad_experiment_observation","ad_creative_draft"))db.update("DELETE FROM "+t);
        cms=mock(CmsCampaignDataService.class);
        service=new AdOverseasOpsService(db,json,cms,mock(AiModelGateway.class),mock(AgentTelemetryService.class),data);
    }
    String row(String campaign,int age,double revenue){return """
        {"campaignId":"%s","platform":"TikTok","cohortDate":"%s","ageDay":%d,"acquisitions":100,"spendUsd":100,"cumulativeRevenueUsd":%s,"activeUsers":40}
        """.formatted(campaign,LocalDate.now().minusDays(40),age,revenue);}
    @Test void emptyCohortsAreEmptyAndMissingFxIsRejected(){
        assertThat((List<?>)data.cohort("",60,"USD").get("cohorts")).isEmpty();
        assertThatThrownBy(()->data.cohort("",60,"CNY")).hasMessageContaining("汇率");
    }
    @Test void cumulativeRevenueIsNotDoubleCountedAndMaturityIsPreserved() throws Exception {
        data.importCohorts(json.readTree("["+row("actual-1",0,30)+","+row("actual-1",7,110)+"]"));
        data.importCohorts(json.readTree("["+row("actual-1",7,110)+"]"));
        var result=data.cohort("TikTok",60,"USD");var cohorts=(List<Map<String,Object>>)result.get("cohorts");
        assertThat(cohorts).hasSize(1);assertThat(cohorts.get(0).get("d7Roas")).isEqualTo(1.1);
        assertThat(cohorts.get(0).get("d30Roas")).isNull();assertThat(cohorts.get(0).get("paybackDays")).isEqualTo(7);
        assertThat(cohorts.get(0).get("spend")).isEqualTo(100d);
    }
    @Test void invalidBatchRollsBackAndFutureObservationIsRejected() throws Exception {
        data.importCohorts(json.readTree("["+row("a",0,30)+","+row("a",7,110)+"]"));
        var invalid=json.readTree("["+row("b",0,20)+","+row("a",7,10)+"]");
        assertThatThrownBy(()->data.importCohorts(invalid)).hasMessageContaining("不能下降");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM ad_cohort_observation WHERE campaign_id='b'",Integer.class)).isZero();
        assertThatThrownBy(()->data.importCohorts(json.readTree("["+row("a",90,200)+"]"))).hasMessageContaining("不能晚于今天");
    }
    @Test void datedFxConvertsOnlyDisplay() throws Exception {
        data.importCohorts(json.readTree("["+row("a",0,30)+"]"));
        data.saveRates(json.readTree("{\"date\":\""+LocalDate.now()+"\",\"source\":\"test supplied rate\",\"rates\":{\"CNY\":7.2}}"));
        var rows=(List<Map<String,Object>>)data.cohort("",60,"CNY").get("cohorts");
        assertThat(rows.get(0).get("spend")).isEqualTo(720d);assertThat(rows.get(0).get("d0Roas")).isEqualTo(.3);
        assertThat(db.queryForObject("SELECT spend_usd FROM ad_cohort_observation",BigDecimal.class)).isEqualByComparingTo("100");
    }
    @Test void configuredThresholdIsUsedAndMissingAssetsAreNotSuccess(){
        when(cms.hasPlatformObservation(any(),any())).thenReturn(true);
        when(cms.listCampaigns("","",250)).thenReturn(List.of(new CmsCampaignDataService.CampaignSummary("c","test","local","ch","Facebook","US","PASS","USD",BigDecimal.valueOf(90),BigDecimal.ZERO,BigDecimal.valueOf(-90),BigDecimal.ZERO,null,false,"",0,"")));
        when(cms.loadCampaignEvidence(eq("c"),anyString(),anyString(),any(),any())).thenReturn(new CmsReportSource.Campaign("c","c","local","ch","USD",null,List.of(new CmsReportSource.Day(LocalDate.now(),BigDecimal.valueOf(90),BigDecimal.ZERO,1000,20,0)),List.of(),List.of()));
        when(cms.isLocalWorkspace()).thenReturn(true);
        service.saveAutomationRule(null,"threshold","STOP_LOSS","ALL","{\"minSpend\":100,\"timezone\":\"UTC\"}","PAUSE_CAMPAIGN","{}",false,true,"test");
        var rule=service.listAutomationRules().get(0);long id=((Number)rule.get("id")).longValue();
        assertThat(service.evaluateAutomationRules("test").get("triggeredCount")).isEqualTo(0);
        service.saveAutomationRule(id,"threshold","STOP_LOSS","ALL","{\"minSpend\":50,\"timezone\":\"UTC\"}","PAUSE_CAMPAIGN","{}",false,true,"test");
        assertThat(service.evaluateAutomationRules("test").get("executedCount")).isEqualTo(0);
        assertThat(service.listAutomationLogs(10).get(0).get("executionStatus")).isEqualTo("PENDING_MANUAL");
        assertThat(service.evaluateAutomationRules("test").get("triggeredCount")).isEqualTo(0);
    }
    @Test void aiGenerationDoesNotReturnTemplatesWhenUnconfigured(){
        assertThatThrownBy(()->service.generateLocalizedCreatives("c","Solar","DE","de-DE","Google","test")).hasMessageContaining("配置 AI");
        var audit=service.auditCompliance("100% guaranteed profit","Meta","US","test");
        assertThat(audit.get("source")).isEqualTo("KEYWORD_RULES");
        assertThat((Integer)audit.get("riskScore")).isLessThan(85);
    }
    @Test void scheduleIsOffByDefaultAndCanBeSaved(){
        assertThat(service.schedule(null).get("enabled")).isEqualTo(false);
        service.scheduledEvaluation();verifyNoInteractions(cms);
        assertThat(service.schedule(true).get("enabled")).isEqualTo(true);
        when(cms.listCampaigns("","",250)).thenReturn(List.of());
        service.scheduledEvaluation();verify(cms,atLeastOnce()).listCampaigns("","",250);
        assertThat(service.schedule(false).get("enabled")).isEqualTo(false);
    }
    @Test void experimentUsesActualCountsAndMultipleComparisonCorrection() throws Exception {
        db.update("INSERT INTO ad_creative_draft(id,headline,body_text) VALUES(100,'Test','Test')");
        String row="{\"draftId\":100,\"variant\":\"%s\",\"reportDate\":\"%s\",\"impressions\":10000,\"clicks\":%s,\"conversions\":10,\"spendUsd\":100,\"revenueUsd\":150}";
        String today=LocalDate.now().toString();
        evidence.importRows("experiments",json.readTree("["+row.formatted("A",today,200)+","+row.formatted("B",today,500)+","+row.formatted("C",today,200)+"]"));
        var experiments=evidence.experiments();assertThat(experiments).hasSize(2);
        assertThat(experiments.get(0).get("controlCtr")).isEqualTo(.02);
        assertThat(experiments.get(0).get("variantCtr")).isEqualTo(.05);
        assertThat(experiments.get(0).get("verdict")).isEqualTo("VARIANT_WINNING");
        assertThat(experiments.get(1).get("verdict")).isEqualTo("INCONCLUSIVE");
        assertThatThrownBy(()->evidence.importRows("experiments",json.readTree("["+row.formatted("B",today,20000)+"]"))).hasMessageContaining("点击不能超过曝光");
    }
    @Test void forecastRequiresEnoughRealAgesAndNeverFillsMeasuredD30() throws Exception {
        data.importCohorts(json.readTree("["+row("trend",0,10)+","+row("trend",3,65)+","+row("trend",7,93)+"]"));
        var cohorts=(List<Map<String,Object>>)data.cohort("",60,"USD").get("cohorts");
        assertThat(cohorts.get(0).get("d30Roas")).isNull();
        assertThat(((Map<?,?>)cohorts.get(0).get("forecast")).get("available")).isEqualTo(true);
    }
    @Test void localExecutionHonorsBudgetCapAndIsIdempotent(){
        db.execute("CREATE TABLE IF NOT EXISTS tiktok_ad_group_info(ad_resource_id VARCHAR, budget DOUBLE,status INT,ad_group_status VARCHAR)");
        db.update("DELETE FROM tiktok_ad_group_info");db.update("INSERT INTO tiktok_ad_group_info VALUES('c',100,1,'ENABLE')");
        when(cms.isLocalWorkspace()).thenReturn(true);when(cms.hasPlatformObservation(any(),any())).thenReturn(true);
        when(cms.listCampaigns("","",250)).thenReturn(List.of(new CmsCampaignDataService.CampaignSummary("c","test","local","ch","TikTok","US","PASS","USD",BigDecimal.valueOf(90),BigDecimal.valueOf(180),BigDecimal.valueOf(90),BigDecimal.valueOf(2),BigDecimal.valueOf(100),false,"",0,"")));
        when(cms.loadCampaignEvidence(eq("c"),anyString(),anyString(),any(),any())).thenReturn(new CmsReportSource.Campaign("c","c","local","ch","USD",BigDecimal.valueOf(100),List.of(new CmsReportSource.Day(LocalDate.now(),BigDecimal.valueOf(90),BigDecimal.valueOf(180),1000,20,10)),List.of(),List.of()));
        service.saveAutomationRule(null,"scale","SCALE_UP","ALL","{\"minRoas\":1.3,\"minPacingPct\":85}","ADJUST_BUDGET","{\"budgetDeltaPct\":15,\"maxDailyBudget\":110}",false,true,"test");
        assertThat(service.evaluateAutomationRules("test").get("executedCount")).isEqualTo(1);
        assertThat(db.queryForObject("SELECT budget FROM tiktok_ad_group_info",Double.class)).isEqualTo(110d);
        assertThat(service.listAutomationLogs(10).get(0).get("executionStatus")).isEqualTo("UPDATED");
        service.evaluateAutomationRules("test");
        assertThat(db.queryForObject("SELECT budget FROM tiktok_ad_group_info",Double.class)).isEqualTo(110d);
    }
    @Test void deletingAllRulesDoesNotRecreateDefaults(){
        for(var rule:service.listAutomationRules())service.deleteAutomationRule(((Number)rule.get("id")).longValue());
        assertThat(service.listAutomationRules()).isEmpty();
    }
}
