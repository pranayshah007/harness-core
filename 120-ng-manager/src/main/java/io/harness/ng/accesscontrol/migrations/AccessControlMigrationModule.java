/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.accesscontrol.migrations;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.accesscontrol.migrations.dao.AccessControlMigrationDAO;
import io.harness.ng.accesscontrol.migrations.dao.AccessControlMigrationDAOImpl;
import io.harness.ng.accesscontrol.migrations.repositories.AccessControlMigrationRepository;
import io.harness.ng.accesscontrol.migrations.services.AccessControlMigrationService;
import io.harness.ng.accesscontrol.migrations.services.AccessControlMigrationServiceImpl;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.resourcegroupclient.remote.ResourceGroupClient;
import io.harness.user.remote.UserClient;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@OwnedBy(HarnessTeam.PL)
public class AccessControlMigrationModule extends AbstractModule {
  private static AccessControlMigrationModule instance;

  public static synchronized AccessControlMigrationModule getInstance() {
    if (instance == null) {
      instance = new AccessControlMigrationModule();
    }
    return instance;
  }

  @Override
  protected void configure() {
    registerRequiredBindings();
    bind(AccessControlMigrationService.class).to(AccessControlMigrationServiceImpl.class).in(Scopes.SINGLETON);
    bind(AccessControlMigrationDAO.class).to(AccessControlMigrationDAOImpl.class).in(Scopes.SINGLETON);
  }

  public void registerRequiredBindings() {
    requireBinding(AccessControlMigrationRepository.class);
    requireBinding(UserClient.class);
    requireBinding(NgUserService.class);
    requireBinding(ProjectService.class);
    requireBinding(OrganizationService.class);
    requireBinding(ResourceGroupClient.class);
    requireBinding(AccessControlAdminClient.class);
  }
}
