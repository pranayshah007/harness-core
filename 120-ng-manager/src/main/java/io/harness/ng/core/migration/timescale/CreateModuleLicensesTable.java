/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.timescale;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(GTM)
public class CreateModuleLicensesTable extends NGAbstractTimeScaleMigration {
  public static final String MODULE_LICENSES_TABLE_SQL_FILE = "timescale/create_module_licenses_table.sql";

  @Override
  public String getFileName() {
    return MODULE_LICENSES_TABLE_SQL_FILE;
  }
}