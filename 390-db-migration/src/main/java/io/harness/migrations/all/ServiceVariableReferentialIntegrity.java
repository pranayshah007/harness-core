/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.Base.ID_KEY2;
import static software.wings.common.Constants.ACCOUNT_ID_KEY;
import static software.wings.common.Constants.APP_ID_KEY;

import io.harness.migrations.Migration;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.ServiceVariable;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by brett on 5/1/18.
 */
@Slf4j
public class ServiceVariableReferentialIntegrity implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info("Checking service variables for invalid parent references");

    UpdateOperations<ServiceVariable> updateOperations =
        wingsPersistence.createUpdateOperations(ServiceVariable.class).unset("parentServiceVariableId");

    List<Account> accounts = wingsPersistence.createQuery(Account.class, excludeAuthority).asList();
    log.info("Checking {} accounts", accounts.size());
    for (Account account : accounts) {
      List<Application> apps =
          wingsPersistence.createQuery(Application.class).filter(ACCOUNT_ID_KEY, account.getUuid()).asList();
      log.info("Checking {} applications in account {}", apps.size(), account.getAccountName());
      for (Application app : apps) {
        List<ServiceVariable> refVariables = wingsPersistence.createQuery(ServiceVariable.class)
                                                 .filter(APP_ID_KEY, app.getUuid())
                                                 .field("parentServiceVariableId")
                                                 .exists()
                                                 .asList();
        log.info("  Checking {} variables in application {}", refVariables.size(), app.getName());
        for (ServiceVariable var : refVariables) {
          String parentId = var.getParentServiceVariableId();
          ServiceVariable parent = wingsPersistence.createQuery(ServiceVariable.class)
                                       .filter(APP_ID_KEY, app.getUuid())
                                       .filter(ID_KEY2, parentId)
                                       .get();
          if (parent == null) {
            log.info("    Clearing invalid parent reference in {}", var.getName());
            wingsPersistence.update(var, updateOperations);
          }
        }
      }
    }
  }
}
