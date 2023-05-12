/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.MigrationDetails;
import io.harness.migration.MigrationProvider;
import io.harness.migration.entities.NGSchema;

import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public class IdpMigrationProvider implements MigrationProvider {
  @Override
  public String getServiceName() {
    return "idpService";
  }

  @Override
  public Class<? extends NGSchema> getSchemaClass() {
    return IdpMigrationSchema.class;
  }

  @Override
  public List<Class<? extends MigrationDetails>> getMigrationDetailsList() {
    return new ArrayList<Class<? extends MigrationDetails>>() {
      { add(IdpBGMigrationDetails.class); }
    };
  }
}
