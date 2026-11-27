package com.afs.agent;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local-cms")
@Import({HistoricalBusinessArchive.class, HistoricalLogReport.class, LocalWorkspaceData.class,
        ChannelCredentialController.class, OptimizationController.class, AgentCopilotController.class,
        CmsReportSource.class, CmsCampaignDataService.class, LegacyContext.class,
        AiModelGateway.class, JdbcCheckpointSaver.class, AgentTelemetryService.class,
        AgentSafetyPolicyService.class, OfflineReplayEvaluator.class, FiveAgentOrchestrator.class,
        AdOpsInsightsService.class, AdOpsInsightsController.class,
        AdAgentExtensionsService.class, AdAgentExtensionsController.class,
        AdOverseasOpsService.class, AdOverseasOpsController.class, OverseasDataService.class, OverseasEvidenceService.class,
        AdOptimizationGraph.class, AdOpsCopilotGraph.class,
        OptimizationWorkflow.class, ExperimentAdvisor.class, DiagnosticAgents.class,
        DemoWorkspaceSeed.class, WorkspaceFixtureNames.class, WorkspaceReportIdentifiers.class,
        RegisteredSiteDefaults.class, WorkspaceCampaignIds.class, WorkspaceSiteBindings.class,
        WorkspacePlatformIdentifiers.class, RealisticPlatformDataMigration.class,
        RealisticWorkspaceExpansion.class, RealisticReportConfigs.class, AiImageGenerator.class,
        PromptRegistry.class, SemanticCache.class, RagService.class, AiEvalService.class,
        AiJobQueue.class, AiPlatformBootstrap.class, AiPlatformController.class})
public class LocalWorkspaceConfiguration {}
