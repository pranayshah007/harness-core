/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.migration;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.migration.MigrationDetails;
import io.harness.migration.NGMigration;
import io.harness.migration.beans.MigrationType;
import io.harness.ng.core.migration.timescale.AddChartVersionToCDStageHelmManifestTable;
import io.harness.ng.core.migration.timescale.AddColumnsToCDStageTable;
import io.harness.ng.core.migration.timescale.AddDeletedAtColumns;
import io.harness.ng.core.migration.timescale.AddIndexToServiceInfraInfoTable;
import io.harness.ng.core.migration.timescale.AddModuleTypeSpecificColumnsToModuleLicensesTable;
import io.harness.ng.core.migration.timescale.AddRollbackDurationToServiceInfraInfoTable;
import io.harness.ng.core.migration.timescale.CreateCDStageHelmManifestTable;
import io.harness.ng.core.migration.timescale.CreateCDStageTable;
import io.harness.ng.core.migration.timescale.CreateConnectorsTable;
import io.harness.ng.core.migration.timescale.CreateModuleLicensesTable;
import io.harness.ng.core.migration.timescale.CreateNgUserTable;
import io.harness.ng.core.migration.timescale.CreateRuntimeInputsInfoTable;
import io.harness.ng.core.migration.timescale.CreateServiceInstancesLicenseDailyReport;
import io.harness.ng.core.migration.timescale.CreateServicesLicenseDailyReport;
import io.harness.ng.core.migration.timescale.CreateStageTable;
import io.harness.ng.core.migration.timescale.GetActiveServicesByDateFunction;
import io.harness.ng.core.migration.timescale.GetServiceInstancesByDateFunction;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
public class NGCoreTimeScaleMigrationDetails implements MigrationDetails {
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
        .add(Pair.of(1, AddRollbackDurationToServiceInfraInfoTable.class))
        .add(Pair.of(2, CreateModuleLicensesTable.class))
        .add(Pair.of(3, GetServiceInstancesByDateFunction.class))
        .add(Pair.of(4, GetActiveServicesByDateFunction.class))
        .add(Pair.of(5, AddModuleTypeSpecificColumnsToModuleLicensesTable.class))
        .add(Pair.of(6, AddIndexToServiceInfraInfoTable.class))
        .add(Pair.of(7, CreateConnectorsTable.class))
        .add(Pair.of(8, CreateNgUserTable.class))
        .add(Pair.of(9, AddDeletedAtColumns.class))
        .add(Pair.of(10, CreateRuntimeInputsInfoTable.class))
        .add(Pair.of(11, CreateStageTable.class))
        .add(Pair.of(12, CreateCDStageTable.class))
        .add(Pair.of(13, AddColumnsToCDStageTable.class))
        .add(Pair.of(14, GetActiveServicesByDateFunction.class))
        .add(Pair.of(15, GetServiceInstancesByDateFunction.class))
        .add(Pair.of(16, CreateServiceInstancesLicenseDailyReport.class))
        .add(Pair.of(17, CreateServicesLicenseDailyReport.class))
        .add(Pair.of(18, CreateCDStageHelmManifestTable.class))
        .add(Pair.of(19, AddChartVersionToCDStageHelmManifestTable.class))
        .build();
  }
}
