/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.licensing.migrations.licenses;

import static io.harness.annotations.dev.HarnessTeam.PLG;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.entities.modules.CFModuleLicense;
import io.harness.migration.NGMigration;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@OwnedBy(PLG)
public class LocalAdminCFLicenseMigration implements NGMigration {
  @Inject MongoTemplate mongoTemplate;
  private final String ADMIN_ACCOUNT_IDENTIFIER = "kmpySmUISimoRrJL6NL73w";
  private static final int FREE_FEATURE_FLAG_UNITS = 2;
  private static final long FREE_CLIENT_MAUS = 25000;

  @Override
  public void migrate() {
    log.info("Create Admin Account CF module license for local dev to show modules ");
    CFModuleLicense cfModuleLicense = CFModuleLicense.builder().build();
    cfModuleLicense.setAccountIdentifier(ADMIN_ACCOUNT_IDENTIFIER);
    cfModuleLicense.setModuleType(ModuleType.CF);
    cfModuleLicense.setEdition(Edition.FREE);
    cfModuleLicense.setStatus(LicenseStatus.ACTIVE);
    cfModuleLicense.setStartTime(System.currentTimeMillis());
    cfModuleLicense.setExpiryTime(Long.MAX_VALUE);
    cfModuleLicense.setSelfService(true);
    cfModuleLicense.setNumberOfUsers(FREE_FEATURE_FLAG_UNITS);
    cfModuleLicense.setNumberOfClientMAUs(FREE_CLIENT_MAUS);

    mongoTemplate.save(cfModuleLicense);
    log.info("Finish create Admin Account CF module license before NG manager start");
  }
}
