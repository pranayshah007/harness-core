/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import io.harness.migration.MigrationDetails;
import io.harness.migration.MigrationProvider;
import io.harness.migration.entities.NGSchema;
import io.harness.ng.core.migration.schema.OrganizationSchema;

import java.util.ArrayList;
import java.util.List;

public class OrganizationMigrationProvider implements MigrationProvider {
  @Override
  public String getServiceName() {
    return "organizations";
  }

  @Override
  public Class<? extends NGSchema> getSchemaClass() {
    return OrganizationSchema.class;
  }

  @Override
  public List<Class<? extends MigrationDetails>> getMigrationDetailsList() {
    return new ArrayList<Class<? extends MigrationDetails>>() {
      { add(OrganizationBackgroundMigrationDetails.class); }
    };
  }
}
