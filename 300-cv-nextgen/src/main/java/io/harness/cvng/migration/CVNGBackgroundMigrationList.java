
package io.harness.cvng.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.migration.list.AddDefaultFieldsToMonitoredService;
import io.harness.cvng.migration.list.AddDeploymentMonitoringSourcePerpetualTask;
import io.harness.cvng.migration.list.AddMonitoringSourcesToVerificationJobMigration;
import io.harness.cvng.migration.list.AppDCustomMetricForExistingProjects;
import io.harness.cvng.migration.list.CVNGBaseMigration;
import io.harness.cvng.migration.list.CleanUpMonitoringSourcePerpetualTask;
import io.harness.cvng.migration.list.CleanUpOldDocuments;
import io.harness.cvng.migration.list.CleanupDeprecatedDocuments;
import io.harness.cvng.migration.list.CreateDefaultVerificationJobsMigration;
import io.harness.cvng.migration.list.DeleteInvalidOrchestratorsMigration;
import io.harness.cvng.migration.list.DeleteOrchestratorWithInvalidVerificationTaskId;
import io.harness.cvng.migration.list.DeleteSLISLOMigration;
import io.harness.cvng.migration.list.EnableExistingCVConfigs;
import io.harness.cvng.migration.list.FixOrchestratorStatusMigration;
import io.harness.cvng.migration.list.FixRuntimeParamInCanaryBlueGreenVerificationJob;
import io.harness.cvng.migration.list.FixRuntimeParamsInDefaultHealthJob;
import io.harness.cvng.migration.list.MigrateSetupEvents;
import io.harness.cvng.migration.list.RecoverMonitoringSourceWorkerId;
import io.harness.cvng.migration.list.RecreateMetricPackAndThresholdMigration;
import io.harness.cvng.migration.list.UpdateActivitySourceTasksMigration;
import io.harness.cvng.migration.list.UpdateActivityStatusMigration;
import io.harness.cvng.migration.list.UpdateApdexMetricCriteria;
import io.harness.cvng.migration.list.UpdateCvConfigPerpetualTasksMigration;
import io.harness.cvng.migration.list.UpdateRiskIntToRiskEnum;

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
        .add(Pair.of(8, UpdateActivitySourceTasksMigration.class))
        .add(Pair.of(9, AddDeploymentMonitoringSourcePerpetualTask.class))
        .add(Pair.of(10, RecoverMonitoringSourceWorkerId.class))
        .add(Pair.of(11, FixRuntimeParamsInDefaultHealthJob.class))
        .add(Pair.of(12, CreateDefaultVerificationJobsMigration.class))
        .add(Pair.of(13, FixRuntimeParamInCanaryBlueGreenVerificationJob.class))
        .add(Pair.of(14, UpdateApdexMetricCriteria.class))
        .add(Pair.of(15, FixRuntimeParamInCanaryBlueGreenVerificationJob.class))
        .add(Pair.of(16, CVNGBaseMigration.class))
        .add(Pair.of(17, DeleteInvalidOrchestratorsMigration.class))
        .add(Pair.of(18, EnableExistingCVConfigs.class))
        .add(Pair.of(19, CleanupDeprecatedDocuments.class))
        .add(Pair.of(20, CleanUpOldDocuments.class))
        .add(Pair.of(21, FixOrchestratorStatusMigration.class))
        .add(Pair.of(22, MigrateSetupEvents.class))
        .add(Pair.of(23, CleanUpMonitoringSourcePerpetualTask.class))
        .add(Pair.of(24, DeleteOrchestratorWithInvalidVerificationTaskId.class))
        .add(Pair.of(25, DeleteSLISLOMigration.class))
        .add(Pair.of(26, AddDefaultFieldsToMonitoredService.class))
        .add(Pair.of(27, AppDCustomMetricForExistingProjects.class))
        .add(Pair.of(28, DeleteSLISLOMigration.class))
        .build();
  }
}
