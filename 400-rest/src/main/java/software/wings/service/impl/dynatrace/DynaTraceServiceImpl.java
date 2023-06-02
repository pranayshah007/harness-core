/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.dynatrace;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.dto.ThirdPartyApiCallLog.createApiCallLog;

import io.harness.eraro.ErrorCode;
import io.harness.exception.VerificationOperationException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.DynaTraceConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse.VerificationLoadResponse;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.dynatrace.DynaTraceDelegateService;
import software.wings.service.intfc.dynatrace.DynaTraceService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by Pranjal on 09/12/2018
 */
@Singleton
@Slf4j
public class DynaTraceServiceImpl implements DynaTraceService {
  @Inject private SettingsService settingsService;
  @Inject private SecretManager secretManager;
  @Inject private DelegateProxyFactory delegateProxyFactory;

  @Override
  public VerificationNodeDataSetupResponse getMetricsWithDataForNode(DynaTraceSetupTestNodeData setupTestNodeData) {
    try {
      final SettingAttribute settingAttribute = settingsService.get(setupTestNodeData.getSettingId());
      setupTestNodeData.setFromTime(TimeUnit.SECONDS.toMillis(setupTestNodeData.getFromTime()));
      setupTestNodeData.setToTime(TimeUnit.SECONDS.toMillis(setupTestNodeData.getToTime()));
      List<EncryptedDataDetail> encryptionDetails =
          secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(settingAttribute.getAccountId())
                                            .appId(GLOBAL_APP_ID)
                                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT * 3)
                                            .build();
      List<DynaTraceMetricDataResponse> response =
          delegateProxyFactory.getV2(DynaTraceDelegateService.class, syncTaskContext)
              .getMetricsWithDataForNode((DynaTraceConfig) settingAttribute.getValue(), encryptionDetails,
                  setupTestNodeData, createApiCallLog(settingAttribute.getAccountId(), setupTestNodeData.getGuid()));
      if (response.isEmpty()) {
        return VerificationNodeDataSetupResponse.builder()
            .providerReachable(true)
            .loadResponse(VerificationLoadResponse.builder().isLoadPresent(false).build())
            .build();
      } else {
        return VerificationNodeDataSetupResponse.builder()
            .providerReachable(true)
            .dataForNode(response)
            .loadResponse(VerificationLoadResponse.builder()
                              .isLoadPresent(isDataPresent(response))
                              .loadResponse(response)
                              .build())
            .build();
      }
    } catch (Exception e) {
      log.info("error getting metric data for node", e);
      throw new VerificationOperationException(
          ErrorCode.DYNA_TRACE_ERROR, "Error in getting metric data for the node. " + e.getMessage());
    }
  }

  @Override
  public List<DynaTraceApplication> getServices(String settingId, boolean shouldResolveAllServices) {
    if (isEmpty(settingId)) {
      return null;
    }
    final SettingAttribute settingAttribute = settingsService.get(settingId);
    DynaTraceConfig dynaTraceConfig = (DynaTraceConfig) settingAttribute.getValue();
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(settingAttribute.getAccountId())
                                          .appId(GLOBAL_APP_ID)
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT * 3)
                                          .build();
    return delegateProxyFactory.getV2(DynaTraceDelegateService.class, syncTaskContext)
        .getServices((DynaTraceConfig) settingAttribute.getValue(), encryptionDetails,
            createApiCallLog(settingAttribute.getAccountId(), generateUuid()), shouldResolveAllServices);
  }

  @Override
  public String resolveDynatraceServiceNameToId(String settingId, String serviceName) {
    if (isEmpty(settingId) || isEmpty(serviceName)) {
      throw new DataCollectionException("Invalid SettingId or ServiceName");
    }
    List<DynaTraceApplication> dynatraceServiceList = getServices(settingId, true);
    if (isNotEmpty(dynatraceServiceList)) {
      Optional<DynaTraceApplication> matchedService =
          dynatraceServiceList.stream().filter(service -> service.getDisplayName().equals(serviceName)).findFirst();
      if (matchedService.isPresent()) {
        return matchedService.get().getEntityId();
      }
    }
    throw new DataCollectionException("Unable to resolve the dynatrace service name" + serviceName + " to an ID");
  }

  @Override
  public boolean validateDynatraceServiceId(String settingId, String serviceId) {
    List<DynaTraceApplication> dynatraceServiceList = getServices(settingId, true);
    if (isNotEmpty(dynatraceServiceList)) {
      return dynatraceServiceList.stream().anyMatch(service -> service.getEntityId().equals(serviceId));
    }
    return false;
  }

  private boolean isDataPresent(List<DynaTraceMetricDataResponse> responses) {
    AtomicBoolean isDataPresent = new AtomicBoolean(false);
    responses.forEach(response -> {
      if (isNotEmpty(response.getResult().getDataPoints())) {
        isDataPresent.set(true);
        return;
      }
    });
    return isDataPresent.get();
  }
}
