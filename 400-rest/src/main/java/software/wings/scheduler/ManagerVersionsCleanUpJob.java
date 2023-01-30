/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import io.harness.beans.FeatureName;
import io.harness.network.Http;
import io.harness.persistence.HPersistence;

import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import io.jsonwebtoken.lang.Collections;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
// Although this is using getDelegateVersions, they are in fact manager versions.
public class ManagerVersionsCleanUpJob implements Runnable {
  private final ConfigurationController configurationController;
  private final HPersistence persistence;
  private final MainConfiguration mainConfiguration;
  private final AccountService accountService;

  @Inject
  public ManagerVersionsCleanUpJob(ConfigurationController configurationController, HPersistence hPersistence,
      MainConfiguration mainConfiguration, AccountService accountService) {
    this.configurationController = configurationController;
    this.persistence = hPersistence;
    this.mainConfiguration = mainConfiguration;
    this.accountService = accountService;
  }

  public void cleanUpDelegateVersions() {
    if (!configurationController.isPrimary()) {
      log.debug("Manager is not primary, it's not allowed for cleanup job. Continuing...");
      return;
    }

    Account globalAccount = persistence.get(Account.class, Account.GLOBAL_ACCOUNT_ID);

    if (globalAccount == null || globalAccount.getDelegateConfiguration() == null) {
      log.debug("There's no global account to cleanup or it has no delegateConfiguration. Continuing...");
      return;
    }

    String primaryVersion = configurationController.getPrimaryVersion();
    log.debug("Retrieved primary version: {}", primaryVersion);

    List<String> versionsToCheck = globalAccount.getDelegateConfiguration().getDelegateVersions();

    if (Collections.isEmpty(versionsToCheck)) {
      log.debug("There are no versions to cleanup. Continuing...");
      return;
    }

    UpdateOperations<Account> updateOperations = persistence.createUpdateOperations(Account.class);
    AtomicBoolean hasUpdateOperations = new AtomicBoolean(false);

    log.debug("Retrieved {} manager version/s for analysis.", versionsToCheck.size());
    versionsToCheck.forEach(version -> {
      if (!version.equals(primaryVersion) && !checkManagerHealth(version)) {
        log.info("Version {} failed the health check, creating an update operation to remove it.", version);
        updateOperations.removeAll(AccountKeys.DELEGATE_CONFIGURATION_DELEGATE_VERSIONS, version);
        hasUpdateOperations.set(true);
      }
    });

    if (hasUpdateOperations.get()) {
      persistence.update(globalAccount, updateOperations);
    } else {
      log.debug("There were no update operations to perform.");
    }
  }

  @Override
  public void run() {
    if (accountService.isFeatureFlagEnabled(
            FeatureName.CLEAN_UP_OLD_MANAGER_VERSIONS.toString(), Account.GLOBAL_ACCOUNT_ID)) {
      cleanUpDelegateVersions();
    } else {
      log.debug(
          "The Feature Flag {} is disabled for the Account ID {}. The old Manager versions will no be periodically cleaned up.",
          FeatureName.CLEAN_UP_OLD_MANAGER_VERSIONS, Account.GLOBAL_ACCOUNT_ID);
    }
  }

  private boolean checkManagerHealth(String version) {
    String url = mainConfiguration.getApiUrl() + "/api/health";

    OkHttpClient httpClient = Http.getUnsafeOkHttpClient(url);
    Request request = new Request.Builder().url(url).get().addHeader("version", version).build();

    Response response;
    try {
      log.debug("Calling health check at {} for version {}", url, version);
      response = httpClient.newCall(request).execute();

      return isHealthyResponse(response);
    } catch (IOException ioe) {
      String errorMessage = String.format(
          "There was a an error reaching the health API. To be safe, the version %s won't be deleted.", version);
      log.error(errorMessage, ioe);
      return true;
    }
  }

  private boolean isHealthyResponse(Response response) throws IOException {
    if (response.isSuccessful()) {
      ResponseBody body = response.body();
      if (body != null) {
        String resultString = body.string();

        return resultString.contains("healthy");
      }
    }

    return false;
  }
}
