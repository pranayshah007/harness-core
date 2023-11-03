/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.datadeletion.gcp.step;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.billing.GcpServiceAccountServiceImpl.getCredentials;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionStep.GCP_SERVICE_ACCOUNTS;
import static io.harness.ccm.serviceAccount.GcpServiceAccountServiceImpl.toGoogleCredential;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionRecord;
import io.harness.ccm.config.GcpServiceAccount;
import io.harness.ccm.config.GcpServiceAccountDao;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.iam.v1.Iam;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.IOException;
import java.security.GeneralSecurityException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@OwnedBy(CE)
public class GCPServiceAccountsCleanup {
  private static final String GOOGLE_CREDENTIALS_PATH = "GOOGLE_CREDENTIALS_PATH";

  @Autowired GcpServiceAccountDao gcpServiceAccountDao;

  public boolean delete(String accountId, DataDeletionRecord dataDeletionRecord, boolean dryRun)
      throws GeneralSecurityException, IOException {
    GcpServiceAccount serviceAccount = gcpServiceAccountDao.getByAccountId(accountId);
    if (serviceAccount == null || StringUtils.isEmpty(serviceAccount.getEmail())) {
      return true;
    }
    Iam service;
    try {
      service = initService();
    } catch (IOException | GeneralSecurityException e) {
      log.error("Unable to initialize service", e);
      throw e;
    }
    dataDeletionRecord.getRecords().get(GCP_SERVICE_ACCOUNTS.name()).setRecordsCount(1L);
    if (!dryRun) {
      String serviceAccountEmail = serviceAccount.getEmail();
      try {
        service.projects().serviceAccounts().delete("projects/-/serviceAccounts/" + serviceAccountEmail).execute();
        log.info("Deleted service account: " + serviceAccountEmail);
      } catch (IOException e) {
        log.info("Unable to delete service account", e);
        throw e;
      }
    }
    return true;
  }

  private static Iam initService() throws GeneralSecurityException, IOException {
    ServiceAccountCredentials credentials = getCredentials(GOOGLE_CREDENTIALS_PATH);
    return new Iam
        .Builder(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(),
            toGoogleCredential(credentials))
        .setApplicationName("service-accounts")
        .build();
  }
}
