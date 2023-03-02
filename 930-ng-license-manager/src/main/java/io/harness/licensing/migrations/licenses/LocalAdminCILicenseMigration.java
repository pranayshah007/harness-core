/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.licensing.migrations.licenses;

import static io.harness.annotations.dev.HarnessTeam.PLG;
import static io.harness.licensing.LicenseConstant.UNLIMITED;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.entities.modules.CIModuleLicense;
import io.harness.migration.NGMigration;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@OwnedBy(PLG)
public class LocalAdminCILicenseMigration implements NGMigration {
  @Inject MongoTemplate mongoTemplate;
  private final String ADMIN_ACCOUNT_IDENTIFIER = "kmpySmUISimoRrJL6NL73w";

  @Override
  public void migrate() {
    log.info("Create Admin Account CI module license for local dev to show modules ");
    CIModuleLicense ciModuleLicense = CIModuleLicense.builder().build();
    ciModuleLicense.setAccountIdentifier(ADMIN_ACCOUNT_IDENTIFIER);
    ciModuleLicense.setModuleType(ModuleType.CI);
    ciModuleLicense.setEdition(Edition.FREE);
    ciModuleLicense.setStatus(LicenseStatus.ACTIVE);
    ciModuleLicense.setStartTime(System.currentTimeMillis());
    ciModuleLicense.setExpiryTime(Long.MAX_VALUE);
    ciModuleLicense.setSelfService(true);
    ciModuleLicense.setNumberOfCommitters(Integer.valueOf(UNLIMITED));

    mongoTemplate.save(ciModuleLicense);
    log.info("Finish create Admin Account CI module license before NG manager start");
  }
}
