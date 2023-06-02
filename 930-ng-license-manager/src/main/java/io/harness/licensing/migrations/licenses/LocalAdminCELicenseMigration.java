/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.licensing.migrations.licenses;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.entities.modules.CEModuleLicense;
import io.harness.migration.NGMigration;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@OwnedBy(GTM)
public class LocalAdminCELicenseMigration implements NGMigration {
  @Inject MongoTemplate mongoTemplate;
  private final String ADMIN_ACCOUNT_IDENTIFIER = "kmpySmUISimoRrJL6NL73w";
  private static final long FREE_WORKLOAD = 250000;

  @Override
  public void migrate() {
    log.info("Create Admin Account CE module license for local dev to show modules ");
    CEModuleLicense ceModuleLicense = CEModuleLicense.builder().build();
    ceModuleLicense.setAccountIdentifier(ADMIN_ACCOUNT_IDENTIFIER);
    ceModuleLicense.setModuleType(ModuleType.CE);
    ceModuleLicense.setEdition(Edition.FREE);
    ceModuleLicense.setStatus(LicenseStatus.ACTIVE);
    ceModuleLicense.setStartTime(System.currentTimeMillis());
    ceModuleLicense.setExpiryTime(Long.MAX_VALUE);
    ceModuleLicense.setSelfService(true);
    ceModuleLicense.setSpendLimit(FREE_WORKLOAD);

    mongoTemplate.save(ceModuleLicense);
    log.info("Finish create Admin Account CE module license before NG manager start");
  }
}
