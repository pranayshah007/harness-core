/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.serviceAccount;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.dao.CEGcpServiceAccountDao;
import io.harness.ccm.commons.entities.billing.CEGcpServiceAccount;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.iam.v1.model.ServiceAccount;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class CEGcpServiceAccountServiceImpl implements CEGcpServiceAccountService {
  private CEGcpServiceAccountDao gcpServiceAccountDao;
  private GcpServiceAccountService gcpServiceAccountService;

  @Inject
  public CEGcpServiceAccountServiceImpl(
      CEGcpServiceAccountDao gcpServiceAccountDao, GcpServiceAccountService gcpServiceAccountService) {
    this.gcpServiceAccountDao = gcpServiceAccountDao;
    this.gcpServiceAccountService = gcpServiceAccountService;
  }

  @Override
  public CEGcpServiceAccount create(String accountId, String ccmProjectId) {
    try (AutoLogContext ignore0 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      String serviceAccountId = getServiceAccountId(accountId);
      String displayName = getServiceAccountDisplayName(accountId);
      ServiceAccount serviceAccount = null;
      try {
        serviceAccount = gcpServiceAccountService.create(serviceAccountId, displayName, ccmProjectId);
      } catch (GoogleJsonResponseException e) {
        log.error("Google was unable to create a service account.", e);
      } catch (IOException ioe) {
        log.error("Unable to create service account.", ioe);
      }

      if (serviceAccount != null) {
        CEGcpServiceAccount ceGcpServiceAccount = CEGcpServiceAccount.builder()
                                                      .serviceAccountId(serviceAccountId)
                                                      .gcpUniqueId(serviceAccount.getUniqueId())
                                                      .accountId(accountId)
                                                      .email(serviceAccount.getEmail())
                                                      .build();
        gcpServiceAccountDao.save(ceGcpServiceAccount);
        return ceGcpServiceAccount;
      }
    }
    return null;
  }

  @Override
  public CEGcpServiceAccount provisionAndRetrieveServiceAccount(
      String accountId, String ccmProjectId, String serviceAccountEmailSource) throws IOException {
    CEGcpServiceAccount gcpServiceAccount = gcpServiceAccountDao.getUnassignedServiceAccountByAccountId(accountId);
    if (gcpServiceAccount == null) {
      log.info("Creating gcpServiceAccount");
      CEGcpServiceAccount ceGcpServiceAccount = create(accountId, ccmProjectId);
      log.info("Created gcpServiceAccount.");
      gcpServiceAccountService.setIamPolicies(ceGcpServiceAccount.getEmail(), serviceAccountEmailSource);
      // https://cloud.google.com/pubsub/docs/access-control
      String[] roles = {"roles/bigquery.admin", "roles/pubsub.admin"};
      gcpServiceAccountService.addRolesToServiceAccount(ceGcpServiceAccount.getEmail(), roles, ccmProjectId);
      log.info("Added roles to gcpServiceAccount");
      gcpServiceAccount = ceGcpServiceAccount;
    }
    return gcpServiceAccount;
  }

  private String getServiceAccountId(String accountId) {
    Random random = new Random();
    return "harness-ce-" + getCompliedSubstring(accountId, 5) + "-"
        + getCompliedSubstring(String.valueOf(random.nextInt(99999)), 5);
  }

  private String getServiceAccountDisplayName(String accountId) {
    return "Harness CE " + accountId;
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
