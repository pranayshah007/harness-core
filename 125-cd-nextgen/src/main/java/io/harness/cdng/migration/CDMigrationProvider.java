package io.harness.cdng.migration;

import io.harness.ModuleType;
import io.harness.migration.MigrationDetails;
import io.harness.migration.MigrationProvider;
import io.harness.migration.entities.NGSchema;

import java.util.ArrayList;
import java.util.List;

public class CDMigrationProvider implements MigrationProvider {
  @Override
  public String getServiceName() {
    return ModuleType.CD.getDisplayName();
  }

  @Override
  public Class<? extends NGSchema> getSchemaClass() {
    return CDSchema.class;
  }

  @Override
  public List<Class<? extends MigrationDetails>> getMigrationDetailsList() {
    return new ArrayList<Class<? extends MigrationDetails>>() {
      { add(CDMigrationDetails.class); }
    };
  }
}
