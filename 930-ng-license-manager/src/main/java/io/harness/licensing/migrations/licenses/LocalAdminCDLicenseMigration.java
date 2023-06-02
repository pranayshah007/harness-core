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
import io.harness.cd.CDLicenseType;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.entities.modules.CDModuleLicense;
import io.harness.migration.NGMigration;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@OwnedBy(GTM)
public class LocalAdminCDLicenseMigration implements NGMigration {
  @Inject MongoTemplate mongoTemplate;
  private final String ADMIN_ACCOUNT_IDENTIFIER = "kmpySmUISimoRrJL6NL73w";
  private final int FREE_WORKLOADS = 5;

  @Override
  public void migrate() {
    log.info("Create Admin Account CD module license before NG manager start for local dev usage");
    CDModuleLicense cdModuleLicense = CDModuleLicense.builder().build();
    cdModuleLicense.setAccountIdentifier(ADMIN_ACCOUNT_IDENTIFIER);
    cdModuleLicense.setModuleType(ModuleType.CD);
    cdModuleLicense.setEdition(Edition.FREE);
    cdModuleLicense.setStatus(LicenseStatus.ACTIVE);
    cdModuleLicense.setStartTime(System.currentTimeMillis());
    cdModuleLicense.setExpiryTime(Long.MAX_VALUE);
    cdModuleLicense.setWorkloads(FREE_WORKLOADS);
    cdModuleLicense.setSelfService(true);
    cdModuleLicense.setCdLicenseType(CDLicenseType.SERVICES);

    mongoTemplate.save(cdModuleLicense);
    log.info("Finish create local Admin Account CD module license");
  }
}
