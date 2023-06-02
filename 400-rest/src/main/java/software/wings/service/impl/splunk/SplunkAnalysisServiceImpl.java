/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.splunk;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.dto.ThirdPartyApiCallLog.createApiCallLog;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.SplunkSavedSearch;
import io.harness.cvng.beans.SplunkValidationResponse;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.exception.WingsException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SplunkConfig;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.service.impl.analysis.AnalysisServiceImpl;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse.VerificationLoadResponse;
import software.wings.service.impl.apm.MLServiceUtils;
import software.wings.service.intfc.splunk.SplunkAnalysisService;
import software.wings.service.intfc.splunk.SplunkDelegateService;
import software.wings.sm.StateType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by Pranjal on 08/31/2018
 */
@OwnedBy(CV)
@Singleton
@Slf4j
public class SplunkAnalysisServiceImpl extends AnalysisServiceImpl implements SplunkAnalysisService {
  @Inject private MLServiceUtils mlServiceUtils;
  @Inject private SecretManagerClientService ngSecretService;
  @Override
  public VerificationNodeDataSetupResponse getLogDataByHost(
      String accountId, SplunkSetupTestNodeData setupTestNodeData) {
    long startTime = TimeUnit.SECONDS.toMillis(setupTestNodeData.getFromTime());
    long endTime = TimeUnit.SECONDS.toMillis(setupTestNodeData.getToTime());
    log.info("Starting Log Data collection by Host for account Id : {}, SplunkSetupTestNodeData : {}", accountId,
        setupTestNodeData);

    // gets the settings attributes for given settings id
    final SettingAttribute settingAttribute = settingsService.get(setupTestNodeData.getSettingId());
    log.info("Settings attribute : " + settingAttribute);
    if (settingAttribute == null) {
      throw new WingsException(
          "No " + StateType.SPLUNKV2 + " setting with id: " + setupTestNodeData.getSettingId() + " found");
    }
    ThirdPartyApiCallLog apiCallLog = createApiCallLog(settingAttribute.getAccountId(), setupTestNodeData.getGuid());

    List<EncryptedDataDetail> encryptedDataDetails = getEncryptionDetails(settingAttribute);
    SyncTaskContext taskContext = getSyncTaskContext(accountId);
    List<LogElement> responseWithoutHost =
        delegateProxyFactory.getV2(SplunkDelegateService.class, taskContext)
            .getLogResults((SplunkConfig) settingAttribute.getValue(), encryptedDataDetails,
                setupTestNodeData.getQuery(), setupTestNodeData.getHostNameField(), null, startTime, endTime,
                apiCallLog, 0, setupTestNodeData.isAdvancedQuery());
    if (isEmpty(responseWithoutHost)) {
      return VerificationNodeDataSetupResponse.builder()
          .providerReachable(true)
          .loadResponse(VerificationLoadResponse.builder().isLoadPresent(false).build())
          .build();
    }

    if (setupTestNodeData.isServiceLevel()) {
      return VerificationNodeDataSetupResponse.builder()
          .providerReachable(true)
          .loadResponse(VerificationLoadResponse.builder()
                            .isLoadPresent(!responseWithoutHost.isEmpty())
                            .loadResponse(responseWithoutHost)
                            .build())
          .build();
    }

    String hostName = mlServiceUtils.getHostName(setupTestNodeData);
    List<LogElement> responseWithHost =
        delegateProxyFactory.getV2(SplunkDelegateService.class, taskContext)
            .getLogResults((SplunkConfig) settingAttribute.getValue(), encryptedDataDetails,
                setupTestNodeData.getQuery(), setupTestNodeData.getHostNameField(), hostName, startTime, endTime,
                apiCallLog, 0, setupTestNodeData.isAdvancedQuery());

    return VerificationNodeDataSetupResponse.builder()
        .providerReachable(true)
        .loadResponse(VerificationLoadResponse.builder().loadResponse(responseWithoutHost).isLoadPresent(true).build())
        .dataForNode(responseWithHost)
        .build();
  }

  @Override
  public List<SplunkSavedSearch> getSavedSearches(
      SplunkConnectorDTO splunkConnectorDTO, String orgIdentifier, String projectIdentifier, String requestGuid) {
    NGAccess basicNGAccessObject = BaseNGAccess.builder()
                                       .accountIdentifier(splunkConnectorDTO.getAccountId())
                                       .orgIdentifier(orgIdentifier)
                                       .projectIdentifier(projectIdentifier)
                                       .build();
    List<EncryptedDataDetail> encryptedDataDetails =
        ngSecretService.getEncryptionDetails(basicNGAccessObject, splunkConnectorDTO);
    SyncTaskContext taskContext = getSyncTaskContext(splunkConnectorDTO.getAccountId());
    return delegateProxyFactory.getV2(SplunkDelegateService.class, taskContext)
        .getSavedSearches(splunkConnectorDTO, encryptedDataDetails, requestGuid);
  }

  @Override
  public SplunkValidationResponse getValidationResponse(SplunkConnectorDTO splunkConnectorDTO, String orgIdentifier,
      String projectIdentifier, String query, String requestGuid) {
    NGAccess basicNGAccessObject = BaseNGAccess.builder()
                                       .accountIdentifier(splunkConnectorDTO.getAccountId())
                                       .orgIdentifier(orgIdentifier)
                                       .projectIdentifier(projectIdentifier)
                                       .build();
    List<EncryptedDataDetail> encryptedDataDetails =
        ngSecretService.getEncryptionDetails(basicNGAccessObject, splunkConnectorDTO);
    SyncTaskContext taskContext = getSyncTaskContext(splunkConnectorDTO.getAccountId());
    return delegateProxyFactory.getV2(SplunkDelegateService.class, taskContext)
        .getValidationResponse(splunkConnectorDTO, encryptedDataDetails, query, requestGuid);
  }

  private List<EncryptedDataDetail> getEncryptionDetails(SettingAttribute settingAttribute) {
    return secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
  }

  private SyncTaskContext getSyncTaskContext(String accountId) {
    return SyncTaskContext.builder()
        .accountId(accountId)
        .appId(GLOBAL_APP_ID)
        .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
        .build();
  }
}
