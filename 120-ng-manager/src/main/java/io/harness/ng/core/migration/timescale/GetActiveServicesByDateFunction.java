/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.timescale;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

@OwnedBy(HarnessTeam.CDP)
public class GetActiveServicesByDateFunction extends NGAbstractTimeScaleMigration {
  private static final String GET_ACTIVE_SERVICES_BY_DATE_FUNCTION_FILE_NAME =
      "timescale/get_active_services_by_date_function_timescale.sql";

  public String getFileName() {
    return GET_ACTIVE_SERVICES_BY_DATE_FUNCTION_FILE_NAME;
  }

  @Override
  public boolean executeFullScript() {
    return true;
  }
}
