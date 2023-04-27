/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.timescale;

import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

public class CreateCDStageTable extends NGAbstractTimeScaleMigration {
  public static final String CREATE_CD_STAGE_TABLE_SQL_FILE = "timescale/create_cd_stage_table.sql";

  @Override
  public String getFileName() {
    return CREATE_CD_STAGE_TABLE_SQL_FILE;
  }
}
