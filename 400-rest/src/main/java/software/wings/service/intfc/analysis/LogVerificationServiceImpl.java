/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.analysis;

import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.dto.ThirdPartyApiCallLog.createApiCallLog;

import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.BugsnagConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse.VerificationLoadResponse;
import software.wings.service.impl.bugsnag.BugsnagApplication;
import software.wings.service.impl.bugsnag.BugsnagDelegateService;
import software.wings.service.impl.bugsnag.BugsnagSetupTestData;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.StateType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class LogVerificationServiceImpl implements LogVerificationService {
  @Inject private SettingsService settingsService;
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private SecretManager secretManager;
  @Inject private WaitNotifyEngine waitNotifyEngine;

  @Override
  public Set<BugsnagApplication> getOrgProjectListBugsnag(
      String settingId, String orgId, StateType stateType, boolean shouldGetProjects) {
    final SettingAttribute settingAttribute = settingsService.get(settingId);
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(settingAttribute.getAccountId())
                                          .appId(GLOBAL_APP_ID)
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                          .build();

    switch (stateType) {
      case BUG_SNAG:
        BugsnagConfig config = (BugsnagConfig) settingAttribute.getValue();
        if (shouldGetProjects) {
          return delegateProxyFactory.getV2(BugsnagDelegateService.class, syncTaskContext)
              .getProjects(config, orgId, encryptionDetails, null);
        } else {
          return delegateProxyFactory.getV2(BugsnagDelegateService.class, syncTaskContext)
              .getOrganizations((BugsnagConfig) settingAttribute.getValue(), encryptionDetails, null);
        }

      default:
        throw new WingsException("Unknown state type in getOrgProjectListBugsnag");
    }
  }

  @Override
  public VerificationNodeDataSetupResponse getTestLogData(String accountId, BugsnagSetupTestData bugsnagSetupTestData) {
    log.info(
        "Starting Log Data collection for account Id : {}, BugsnagSetupTestData : {}", accountId, bugsnagSetupTestData);
    // gets the settings attributes for given settings id
    final SettingAttribute settingAttribute = settingsService.get(bugsnagSetupTestData.getSettingId());
    log.info("Settings attribute : " + settingAttribute);
    if (settingAttribute == null) {
      throw new WingsException(
          "No " + StateType.BUG_SNAG + " setting with id: " + bugsnagSetupTestData.getSettingId() + " found");
    }

    List<EncryptedDataDetail> encryptedDataDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
    SyncTaskContext taskContext =
        SyncTaskContext.builder().accountId(accountId).appId(GLOBAL_APP_ID).timeout(DEFAULT_SYNC_CALL_TIMEOUT).build();
    bugsnagSetupTestData.setToTime(System.currentTimeMillis());
    bugsnagSetupTestData.setFromTime(bugsnagSetupTestData.getToTime() - TimeUnit.MINUTES.toMillis(60));
    Object response;
    try {
      response = delegateProxyFactory.getV2(BugsnagDelegateService.class, taskContext)
                     .search((BugsnagConfig) settingAttribute.getValue(), accountId, bugsnagSetupTestData,
                         encryptedDataDetails,
                         createApiCallLog(settingAttribute.getAccountId(), bugsnagSetupTestData.getGuid()));
    } catch (IOException ex) {
      log.info("Error while getting data ", ex);
      return VerificationNodeDataSetupResponse.builder().providerReachable(false).build();
    }
    return VerificationNodeDataSetupResponse.builder()
        .providerReachable(true)
        .loadResponse(VerificationLoadResponse.builder()
                          .isLoadPresent(!((ArrayList) response).isEmpty())
                          .loadResponse(response)
                          .build())
        .build();
  }
}
