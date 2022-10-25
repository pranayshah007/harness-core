/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.config;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.billing.GcpServiceAccountService;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;

import software.wings.beans.Account;
import software.wings.service.intfc.AccountService;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.iam.v1.model.ServiceAccount;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class CEGcpServiceAccountServiceImpl implements CEGcpServiceAccountService {
  private GcpServiceAccountDao gcpServiceAccountDao;
  private GcpServiceAccountService gcpServiceAccountService;
  private AccountService accountService;

  @Inject
  public CEGcpServiceAccountServiceImpl(GcpServiceAccountDao gcpServiceAccountDao,
      GcpServiceAccountService gcpServiceAccountService, AccountService accountService) {
    this.gcpServiceAccountDao = gcpServiceAccountDao;
    this.gcpServiceAccountService = gcpServiceAccountService;
    this.accountService = accountService;
  }

  @Override
  public String create(String accountId) {
    try (AutoLogContext ignore0 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      Account account = accountService.get(accountId);
      String serviceAccountId = getServiceAccountId(account);
      String displayName = getServiceAccountDisplayName(account);
      ServiceAccount serviceAccount = null;
      try {
        serviceAccount = gcpServiceAccountService.create(serviceAccountId, displayName);
      } catch (GoogleJsonResponseException e) {
        log.error("Google was unable to create a service account.", e);
      } catch (IOException ioe) {
        log.error("Unable to create service account.", ioe);
      }

      if (serviceAccount != null) {
        gcpServiceAccountDao.save(GcpServiceAccount.builder()
                                      .serviceAccountId(serviceAccountId)
                                      .gcpUniqueId(serviceAccount.getUniqueId())
                                      .accountId(accountId)
                                      .email(serviceAccount.getEmail())
                                      .build());
        return serviceAccount.getEmail();
      }
    }
    return null;
  }

  @Override
  public GcpServiceAccount getDefaultServiceAccount(String accountId) throws IOException {
    GcpServiceAccount gcpServiceAccount = getByAccountId(accountId);
    if (gcpServiceAccount == null) {
      log.info("Creating gcpServiceAccount");
      String serviceAccountEmail = create(accountId);
      log.info("Created gcpServiceAccount");
      gcpServiceAccountService.setIamPolicies(serviceAccountEmail);
      // https://cloud.google.com/pubsub/docs/access-control
      String[] roles = {"roles/bigquery.admin", "roles/pubsub.admin"};
      gcpServiceAccountService.addRolesToServiceAccount(serviceAccountEmail, roles);
      log.info("Added roles to  gcpServiceAccount");
      gcpServiceAccount = getByAccountId(accountId);
    }
    return gcpServiceAccount;
  }

  @Override
  public GcpServiceAccount getByAccountId(String accountId) {
    return gcpServiceAccountDao.getByAccountId(accountId);
  }

  @Override
  public GcpServiceAccount getByServiceAccountId(String accountId) {
    Account account = accountService.get(accountId);
    String serviceAccountId = getServiceAccountId(account);
    return gcpServiceAccountDao.getByServiceAccountId(serviceAccountId);
  }

  private String getServiceAccountId(Account account) {
    return "harness-ce-" + getCompliedSubstring(account.getAccountName(), 12) + "-"
        + getCompliedSubstring(account.getUuid(), 5);
  }

  private String getServiceAccountDisplayName(Account account) {
    return "Harness CE " + account.getAccountName() + " " + account.getUuid();
  }

  private String getCompliedSubstring(String s, int maxLength) {
    String substring;
    String compliedAccountName = s.toLowerCase().replaceAll("[^a-z0-9]", "");
    if (compliedAccountName.length() < maxLength) {
      substring = compliedAccountName;
    } else {
      substring = compliedAccountName.substring(0, maxLength);
    }
    return substring;
  }
}
