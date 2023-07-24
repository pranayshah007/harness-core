/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.MigrationDetails;
import io.harness.migration.NGMigration;
import io.harness.migration.beans.MigrationType;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineCoreTimeScaleMigrationDetails implements MigrationDetails {
  @Override
  public MigrationType getMigrationTypeName() {
    return MigrationType.TimeScaleMigration;
  }

  @Override
  public boolean isBackground() {
    return false;
  }

  @Override
  public List<Pair<Integer, Class<? extends NGMigration>>> getMigrations() {
    return new ImmutableList.Builder<Pair<Integer, Class<? extends NGMigration>>>()
        .add(Pair.of(1, CreateTimescaleCDCTablesWhereNotExist.class))
        .add(Pair.of(2, UpdateTimescalePipelineExecutionSummary.class))
        .add(Pair.of(3, UpdateTimescaleCIPipelineExecutionSummary.class))
        .add(Pair.of(4, UpdateTimescaleTablePipelineExecutionSummaryCd.class))
        .add(Pair.of(5, UpdateTimescaleTableCIWithTriggerInfo.class))
        .add(Pair.of(6, UpdateTsCIWithPR.class))
        .add(Pair.of(7, UpdateTsCIWithIsRepoPrivate.class))
        .add(Pair.of(8, AddInfrastructureIdentifierInServiceInfraInfoTable.class))
        .add(Pair.of(9, AddGitOpsEnabledInServiceInfraInfoTable.class))
        .add(Pair.of(10, AddInfrastructureNameInServiceInfraInfoTable.class))
        .add(Pair.of(11, CreateTimeScalePipelineExecutionSummary.class))
        .add(Pair.of(12, AddEnvGroupInServiceInfraInfoTable.class))
        .add(Pair.of(13, AddNewTableForRevertExecutionsRevertColumns.class))
        .add(Pair.of(14, AddArtifactDisplayNameToServiceInfraInfoTable.class))
        .add(Pair.of(15, UpdatePipelineExecutionSummaryCdTimescaleTable.class))
        .add(Pair.of(16, AddExecutionFailureDetailsToServiceInfraInfoTable.class))
        .add(Pair.of(17, CreateCustomStageTimeScaleTable.class))
        .add(Pair.of(18, CreateStepExecutionsTimeScaleTable.class))
        .build();
  }
}
