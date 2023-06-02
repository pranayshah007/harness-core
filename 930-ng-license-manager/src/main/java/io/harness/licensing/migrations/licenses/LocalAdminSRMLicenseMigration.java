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
import io.harness.licensing.entities.modules.SRMModuleLicense;
import io.harness.migration.NGMigration;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@OwnedBy(GTM)
public class LocalAdminSRMLicenseMigration implements NGMigration {
  @Inject MongoTemplate mongoTemplate;
  private final String ADMIN_ACCOUNT_IDENTIFIER = "kmpySmUISimoRrJL6NL73w";
  private static final int FREE_TRIAL_SERVICES = 5;

  @Override
  public void migrate() {
    log.info("Create Admin Account SRM module license for local dev to show modules ");
    SRMModuleLicense srmModuleLicense = SRMModuleLicense.builder().build();
    srmModuleLicense.setAccountIdentifier(ADMIN_ACCOUNT_IDENTIFIER);
    srmModuleLicense.setModuleType(ModuleType.SRM);
    srmModuleLicense.setEdition(Edition.FREE);
    srmModuleLicense.setStatus(LicenseStatus.ACTIVE);
    srmModuleLicense.setStartTime(System.currentTimeMillis());
    srmModuleLicense.setExpiryTime(Long.MAX_VALUE);
    srmModuleLicense.setSelfService(true);
    srmModuleLicense.setNumberOfServices(FREE_TRIAL_SERVICES);

    mongoTemplate.save(srmModuleLicense);
    log.info("Finish create Admin Account SRM module license before NG manager start");
  }
}
