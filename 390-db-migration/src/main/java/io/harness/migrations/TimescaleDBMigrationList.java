/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migrations.timescaledb.*;
import io.harness.migrations.timescaledb.data.AddActualInstanceIdToK8sUtilizationData;
import io.harness.migrations.timescaledb.data.CreateAnomaliesDataV2;
import io.harness.migrations.timescaledb.data.CreatePodCountTable;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.PL)
@UtilityClass
public class TimescaleDBMigrationList {
  public static List<Pair<Integer, Class<? extends TimeScaleDBMigration>>> getMigrations() {
    return new ImmutableList.Builder<Pair<Integer, Class<? extends TimeScaleDBMigration>>>()
        .add(Pair.of(1, InitSchemaMigration.class))
        .add(Pair.of(2, InitVerificationSchemaMigration.class))
        .add(Pair.of(3, RenameInstanceMigration.class))
        .add(Pair.of(4, DeploymentAdditionalColumns.class))
        .add(Pair.of(5, ChangeToTimeStampTZ.class))
        .add(Pair.of(6, CreateNewInstanceV2Migration.class))
        .add(Pair.of(7, AddIndexToInstanceV2Migration.class))
        .add(Pair.of(8, AddRollbackToDeployment.class))
        .add(Pair.of(9, AddingToCVDeploymentMetrics.class))
        .add(Pair.of(10, AddSchemaForServiceGuardStats.class))
        .add(Pair.of(11, AddInstancesDeployedToDeployment.class))
        .add(Pair.of(12, UpdateServiceGuardSchema.class))
        .add(Pair.of(13, AddFieldsToWorkflowCVMetrics.class))
        .add(Pair.of(14, AddFieldsToServiceGuardStats.class))
        .add(Pair.of(15, CreateBillingData.class))
        .add(Pair.of(16, CreateKubernetesUtilizationData.class))
        .add(Pair.of(17, CreateUtilizationData.class))
        .add(Pair.of(18, AlterCEUtilizationDataTables.class))
        .add(Pair.of(19, UniqueIndexCEUtilizationDataTables.class))
        .add(Pair.of(20, AddSystemCostBillingData.class))
        .add(Pair.of(21, AddCostEvents.class))
        .add(Pair.of(22, AddDeploymentTagsToDeployment.class))
        .add(Pair.of(23, AddIdleUnallocatedColumns.class))
        .add(Pair.of(24, AddMaxUtilColumns.class))
        .add(Pair.of(25, CreateBillingDataHourly.class))
        .add(Pair.of(26, AddRequestColumnToBillingData.class))
        .add(Pair.of(27, AddPercentagesToCostEvents.class))
        .add(Pair.of(28, AddIndicesForCostEvents.class))
        .add(Pair.of(29, AddNonComputeCostColumnToBillingData.class))
        .add(Pair.of(30, CreateBudgetAlerts.class))
        .add(Pair.of(31, CreatePodCountTable.class))
        .add(Pair.of(32, CreateAnomaliesData.class))
        .add(Pair.of(33, AddExplorerV2Indices.class))
        .add(Pair.of(34, CreateAggregatedBillingTable.class))
        .add(Pair.of(35, CreateAnomaliesDataV2.class))
        .add(Pair.of(36, AddAccountIdStatusIndexToDeployment.class))
        .add(Pair.of(37, AddFeedbackToAnomalies.class))
        .add(Pair.of(38, AddStorageSupportK8sUtilTable.class))
        .add(Pair.of(39, AddSlackNotificationSupportAnomalies.class))
        .add(Pair.of(40, CreatePreAggHourlyTable.class))
        .add(Pair.of(41, InitTriggerFunctions.class))
        .add(Pair.of(42, CreateInstanceStatsHourTable.class))
        .add(Pair.of(43, CreateInstanceStatsDayTable.class))
        .add(Pair.of(44, CreateDeploymentParentTable.class))
        .add(Pair.of(45, CreateDeploymentStageTable.class))
        .add(Pair.of(46, AddAlertTypeColumnToBudgetAlerts.class))
        .add(Pair.of(47, AddActualInstanceIdToK8sUtilizationData.class))
        .add(Pair.of(48, AddNewentityToAnomalies.class))
        .add(Pair.of(49, AddNewIndexToAnomalies.class))
        .add(Pair.of(50, RecommendationsRelatedTables.class))
        .add(Pair.of(51, CreateCeRecommendationTable.class))
        .add(Pair.of(52, CreateNodeInfoTableAndIsAliveFunction.class))
        .add(Pair.of(53, CreateAccountTables.class))
        .add(Pair.of(54, CreateIndexOnKubernetesUtilizationData.class))
        .add(Pair.of(55, AddMaxStorageColumns.class))
        .add(Pair.of(56, CreateServicesEnvPipelinesTable.class))
        .add(Pair.of(57, CreateServicesEnvPipelinesIndex.class))
        .add(Pair.of(58, AddCDNGEntitiesColumns.class))
        .add(Pair.of(59, CreateApplicationTable.class))
        .add(Pair.of(60, CreateServiceTable.class))
        .add(Pair.of(61, CreatePipelineTable.class))
        .add(Pair.of(62, CreateWorkflowTable.class))
        .add(Pair.of(63, CreateTaglinksTables.class))
        .add(Pair.of(64, CreateEnvironmentTable.class))
        .add(Pair.of(65, CreateUserTable.class))
        .add(Pair.of(66, CreateCloudProviderTable.class))
        .add(Pair.of(67, AddAzureColumnAnomaly.class))
        .add(Pair.of(68, AddFailureDetailsToDeployment.class))
        .add(Pair.of(69, AddParentPipelineToDeployment.class))
        .add(Pair.of(70, CreateInfraDefinitionTable.class))
        .add(Pair.of(71, CreateDeploymentStepTable.class))
        .add(Pair.of(72, CreateExecutionInterruptTable.class))
        .build();
  }
}
