/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.migration.list.ActivityBucketCleanupAndMigration;
import io.harness.cvng.migration.list.ActivityCleanupAndBucketMigration;
import io.harness.cvng.migration.list.AddDeploymentMonitoringSourcePerpetualTask;
import io.harness.cvng.migration.list.AddEnabledFlagToSLISLOMigration;
import io.harness.cvng.migration.list.AddEnvRefsToMonitoredServiceMigration;
import io.harness.cvng.migration.list.AddLastDisabledAtToMonitoredServiceMigration;
import io.harness.cvng.migration.list.AddMetricIdentifierInCVConfigsAndMetricPacks;
import io.harness.cvng.migration.list.AddMetricIdentifierToTimeSeriesThreshold;
import io.harness.cvng.migration.list.AddMonitoredServiceToActivityMigration;
import io.harness.cvng.migration.list.AddMonitoredServiceToCVConfigMigration;
import io.harness.cvng.migration.list.AddMonitoredServiceToChangeSourceMigration;
import io.harness.cvng.migration.list.AddMonitoredServiceToHeatMapMigration;
import io.harness.cvng.migration.list.AddMonitoredServiceToWebhookMigration;
import io.harness.cvng.migration.list.AddMonitoringSourcesToVerificationJobMigration;
import io.harness.cvng.migration.list.AddSLIEvaluationTypeToSLOMigration;
import io.harness.cvng.migration.list.AddStartedAtToServiceLevelObjectiveV2;
import io.harness.cvng.migration.list.AddTaskInfoToVerificationTask;
import io.harness.cvng.migration.list.AddTypeToServiceLevelObjectivesV2;
import io.harness.cvng.migration.list.AppDCVConfigCompleteMetricPathMigration;
import io.harness.cvng.migration.list.AppDCustomMetricForExistingProjects;
import io.harness.cvng.migration.list.CVConfigPopulateUuidForThresholds;
import io.harness.cvng.migration.list.CVNGBaseMigration;
import io.harness.cvng.migration.list.CleanUpMonitoringSourcePerpetualTask;
import io.harness.cvng.migration.list.CleanUpOldDocuments;
import io.harness.cvng.migration.list.CleanupDeprecatedDocuments;
import io.harness.cvng.migration.list.CustomHealthCustomPackForExistingProjects;
import io.harness.cvng.migration.list.DeleteActivitiesNotAssociatedWithMonitoredServices;
import io.harness.cvng.migration.list.DeleteHarnessCDChangeSourceMigration;
import io.harness.cvng.migration.list.DeleteInvalidOrchestratorsMigration;
import io.harness.cvng.migration.list.DeleteOldAnalysisOrchestratorMigration;
import io.harness.cvng.migration.list.DeleteOrchestratorWithInvalidVerificationTaskId;
import io.harness.cvng.migration.list.DeleteRedundantSLIsSLOs;
import io.harness.cvng.migration.list.DeleteSLISLOMigration;
import io.harness.cvng.migration.list.ELKMigrationCreateVerificationTaskLiveMonitoring;
import io.harness.cvng.migration.list.EnableExistingCVConfigs;
import io.harness.cvng.migration.list.FixOrchestratorStatusMigration;
import io.harness.cvng.migration.list.FixRuntimeParamInCanaryBlueGreenVerificationJob;
import io.harness.cvng.migration.list.MigrateSLOTargetInSLOV2;
import io.harness.cvng.migration.list.NewRelicCustomPackForExistingProjects;
import io.harness.cvng.migration.list.NoOppMigration;
import io.harness.cvng.migration.list.OrphanMonitoredServicesCleanup;
import io.harness.cvng.migration.list.PrometheusConnectorAuthenticationPerpetualTaskMigration;
import io.harness.cvng.migration.list.RecoverMonitoringSourceWorkerId;
import io.harness.cvng.migration.list.RecreateMetricPackAndThresholdMigration;
import io.harness.cvng.migration.list.SLIBucketCleanupAndSLIRecordMigration;
import io.harness.cvng.migration.list.SLIRecordToSLIRecordBucketMigration;
import io.harness.cvng.migration.list.SetNotRequiredActivityAnalysisStatusToIgnored;
import io.harness.cvng.migration.list.UpdateActivityStatusMigration;
import io.harness.cvng.migration.list.UpdateApdexMetricCriteria;
import io.harness.cvng.migration.list.UpdateCvConfigPerpetualTasksMigration;
import io.harness.cvng.migration.list.UpdateELKCVConfigToNextGenCVConfig;
import io.harness.cvng.migration.list.UpdateMSNotificationChangeCategoriesMigration;
import io.harness.cvng.migration.list.UpdateRiskIntToRiskEnum;
import io.harness.cvng.migration.list.UpdateTagsInServiceLevelObjectiveV2;
import io.harness.cvng.migration.list.WriteServiceLevelObjectivesToV2;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.Pair;

@UtilityClass
@OwnedBy(HarnessTeam.CV)
public class CVNGBackgroundMigrationList {
  /**
   * Add your background migrations to the end of the list with the next sequence number.
   * Make sure your background migration is resumable and with rate limit that does not exhaust
   * the resources.
   */
  public static List<Pair<Integer, Class<? extends CVNGMigration>>> getMigrations() {
    return new ImmutableList.Builder<Pair<Integer, Class<? extends CVNGMigration>>>()
        .add(Pair.of(1, CVNGBaseMigration.class))
        .add(Pair.of(2, RecreateMetricPackAndThresholdMigration.class))
        .add(Pair.of(3, CVNGBaseMigration.class))
        .add(Pair.of(4, AddMonitoringSourcesToVerificationJobMigration.class))
        .add(Pair.of(5, UpdateActivityStatusMigration.class))
        .add(Pair.of(6, UpdateRiskIntToRiskEnum.class))
        .add(Pair.of(7, UpdateCvConfigPerpetualTasksMigration.class))
        .add(Pair.of(8, NoOppMigration.class))
        .add(Pair.of(9, AddDeploymentMonitoringSourcePerpetualTask.class))
        .add(Pair.of(10, RecoverMonitoringSourceWorkerId.class))
        .add(Pair.of(11, NoOppMigration.class))
        .add(Pair.of(12, NoOppMigration.class))
        .add(Pair.of(13, FixRuntimeParamInCanaryBlueGreenVerificationJob.class))
        .add(Pair.of(14, UpdateApdexMetricCriteria.class))
        .add(Pair.of(15, FixRuntimeParamInCanaryBlueGreenVerificationJob.class))
        .add(Pair.of(16, CVNGBaseMigration.class))
        .add(Pair.of(17, DeleteInvalidOrchestratorsMigration.class))
        .add(Pair.of(18, EnableExistingCVConfigs.class))
        .add(Pair.of(19, CleanupDeprecatedDocuments.class))
        .add(Pair.of(20, CleanUpOldDocuments.class))
        .add(Pair.of(21, FixOrchestratorStatusMigration.class))
        .add(Pair.of(22, NoOppMigration.class))
        .add(Pair.of(23, CleanUpMonitoringSourcePerpetualTask.class))
        .add(Pair.of(24, DeleteOrchestratorWithInvalidVerificationTaskId.class))
        .add(Pair.of(25, DeleteSLISLOMigration.class))
        .add(Pair.of(26, NoOppMigration.class))
        .add(Pair.of(27, AppDCustomMetricForExistingProjects.class))
        .add(Pair.of(28, DeleteSLISLOMigration.class))
        .add(Pair.of(29, NewRelicCustomPackForExistingProjects.class))
        .add(Pair.of(30, CustomHealthCustomPackForExistingProjects.class))
        .add(Pair.of(31, AddMonitoredServiceToCVConfigMigration.class))
        .add(Pair.of(32, AddMonitoredServiceToHeatMapMigration.class))
        .add(Pair.of(33, AddMonitoredServiceToChangeSourceMigration.class))
        .add(Pair.of(34, AddMonitoredServiceToCVConfigMigration.class))
        .add(Pair.of(35, AddMonitoredServiceToHeatMapMigration.class))
        .add(Pair.of(36, AddEnvRefsToMonitoredServiceMigration.class))
        // Migration logic was missed in default monitored service create. Adding the migration again.
        .add(Pair.of(37, AddEnvRefsToMonitoredServiceMigration.class))
        .add(Pair.of(38, AddMonitoredServiceToHeatMapMigration.class))
        .add(Pair.of(39, AddMonitoredServiceToActivityMigration.class))
        .add(Pair.of(40, AddMetricIdentifierInCVConfigsAndMetricPacks.class))
        .add(Pair.of(41, AddMetricIdentifierToTimeSeriesThreshold.class))
        .add(Pair.of(42, AddMonitoredServiceToWebhookMigration.class))
        .add(Pair.of(43, AddTaskInfoToVerificationTask.class))
        .add(Pair.of(44, DeleteOldAnalysisOrchestratorMigration.class))
        .add(Pair.of(45, AddMetricIdentifierInCVConfigsAndMetricPacks.class))
        .add(Pair.of(46, AddMetricIdentifierToTimeSeriesThreshold.class))
        .add(Pair.of(47, AppDCVConfigCompleteMetricPathMigration.class))
        .add(Pair.of(48, SetNotRequiredActivityAnalysisStatusToIgnored.class))
        .add(Pair.of(49, PrometheusConnectorAuthenticationPerpetualTaskMigration.class))
        .add(Pair.of(50, AddEnabledFlagToSLISLOMigration.class))
        .add(Pair.of(51, AddLastDisabledAtToMonitoredServiceMigration.class))
        .add(Pair.of(52, WriteServiceLevelObjectivesToV2.class))
        .add(Pair.of(53, AddTypeToServiceLevelObjectivesV2.class))
        .add(Pair.of(54, AddStartedAtToServiceLevelObjectiveV2.class))
        .add(Pair.of(55, DeleteActivitiesNotAssociatedWithMonitoredServices.class))
        .add(Pair.of(56, CVConfigPopulateUuidForThresholds.class))
        .add(Pair.of(57, UpdateTagsInServiceLevelObjectiveV2.class))
        .add(Pair.of(58, UpdateELKCVConfigToNextGenCVConfig.class))
        .add(Pair.of(59, MigrateSLOTargetInSLOV2.class))
        .add(Pair.of(60, ELKMigrationCreateVerificationTaskLiveMonitoring.class))
        .add(Pair.of(61, AddSLIEvaluationTypeToSLOMigration.class))
        .add(Pair.of(62, OrphanMonitoredServicesCleanup.class))
        .add(Pair.of(63, ActivityCleanupAndBucketMigration.class))
        .add(Pair.of(64, UpdateMSNotificationChangeCategoriesMigration.class))
        .add(Pair.of(65, ActivityBucketCleanupAndMigration.class))
        .add(Pair.of(66, DeleteHarnessCDChangeSourceMigration.class))
        .add(Pair.of(67, DeleteRedundantSLIsSLOs.class))
        .add(Pair.of(68, SLIRecordToSLIRecordBucketMigration.class))
        .add(Pair.of(69, SLIBucketCleanupAndSLIRecordMigration.class))
        .build();
  }
}
