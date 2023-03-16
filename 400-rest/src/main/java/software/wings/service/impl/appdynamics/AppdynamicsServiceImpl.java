/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.appdynamics;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.dto.ThirdPartyApiCallLog.NO_STATE_EXECUTION_ID;
import static software.wings.beans.dto.ThirdPartyApiCallLog.createApiCallLog;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.apm.MLServiceUtils;
import software.wings.service.impl.newrelic.NewRelicApplication;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.appdynamics.AppdynamicsDelegateService;
import software.wings.service.intfc.appdynamics.AppdynamicsService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Set;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by rsingh on 4/17/17.
 */
@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(CV)
@TargetModule(HarnessModule._360_CG_MANAGER)
public class AppdynamicsServiceImpl implements AppdynamicsService {
  @Inject private SettingsService settingsService;
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private SecretManager secretManager;
  @Inject private MLServiceUtils mlServiceUtils;
  @Inject private SecretManagerClientService ngSecretService;
  @Override
  public List<NewRelicApplication> getApplications(final String settingId) {
    return this.getApplications(settingId, null, null);
  }

  @Override
  public List<NewRelicApplication> getApplications(String settingId, String appId, String workflowExecutionId) {
    final SettingAttribute settingAttribute = settingsService.get(settingId);
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(settingAttribute.getAccountId())
                                          .appId(GLOBAL_APP_ID)
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                          .build();
    AppDynamicsConfig appDynamicsConfig = (AppDynamicsConfig) settingAttribute.getValue();
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails(appDynamicsConfig, appId, workflowExecutionId);
    return delegateProxyFactory.getV2(AppdynamicsDelegateService.class, syncTaskContext)
        .getAllApplications(appDynamicsConfig, encryptionDetails);
  }

  @Override
  public Set<AppdynamicsTier> getTiers(String settingId, long appdynamicsAppId) {
    return this.getTiers(
        settingId, appdynamicsAppId, ThirdPartyApiCallLog.createApiCallLog(GLOBAL_ACCOUNT_ID, NO_STATE_EXECUTION_ID));
  }

  @Override
  public Set<AppdynamicsTier> getTiers(String settingId, long appdynamicsAppId, ThirdPartyApiCallLog apiCallLog) {
    return this.getTiers(settingId, appdynamicsAppId, null, null, apiCallLog);
  }

  @Override
  public Set<AppdynamicsTier> getTiers(String settingId, long appdynamicsAppId, String appId,
      String workflowExecutionId, ThirdPartyApiCallLog apiCallLog) {
    final SettingAttribute settingAttribute = settingsService.get(settingId);
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(settingAttribute.getAccountId())
                                          .appId(GLOBAL_APP_ID)
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                          .build();
    AppDynamicsConfig appDynamicsConfig = (AppDynamicsConfig) settingAttribute.getValue();
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails(appDynamicsConfig, appId, workflowExecutionId);
    return delegateProxyFactory.getV2(AppdynamicsDelegateService.class, syncTaskContext)
        .getTiers(appDynamicsConfig, appdynamicsAppId, encryptionDetails, apiCallLog);
  }

  @Override
  public VerificationNodeDataSetupResponse getMetricsWithDataForNode(AppdynamicsSetupTestNodeData setupTestNodeData) {
    String hostName = null;
    // check if it is for service level, serviceId is empty then get hostname
    if (!setupTestNodeData.isServiceLevel()) {
      hostName = mlServiceUtils.getHostName(setupTestNodeData);
    }

    try {
      final SettingAttribute settingAttribute = settingsService.get(setupTestNodeData.getSettingId());
      List<EncryptedDataDetail> encryptionDetails =
          secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(settingAttribute.getAccountId())
                                            .appId(GLOBAL_APP_ID)
                                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT * 3)
                                            .build();
      return delegateProxyFactory.getV2(AppdynamicsDelegateService.class, syncTaskContext)
          .getMetricsWithDataForNode((AppDynamicsConfig) settingAttribute.getValue(), encryptionDetails,
              setupTestNodeData, hostName,
              createApiCallLog(settingAttribute.getAccountId(), setupTestNodeData.getGuid()));
    } catch (Exception e) {
      log.info("error getting metric data for node", e);
      throw new WingsException(ErrorCode.APPDYNAMICS_ERROR, e).addParam("reason", e.getMessage());
    }
  }

  @Override
  public NewRelicApplication getAppDynamicsApplication(String connectorId, String appDynamicsApplicationId) {
    return this.getAppDynamicsApplication(connectorId, appDynamicsApplicationId, null, null);
  }

  @Override
  public NewRelicApplication getAppDynamicsApplication(
      String connectorId, String appDynamicsApplicationId, String appId, String workflowExecutionId) {
    try {
      List<NewRelicApplication> apps = getApplications(connectorId, appId, workflowExecutionId);
      NewRelicApplication appDynamicsApp = null;
      for (NewRelicApplication app : apps) {
        if (String.valueOf(app.getId()).equals(appDynamicsApplicationId)) {
          appDynamicsApp = app;
          break;
        }
      }
      return appDynamicsApp;
    } catch (Exception ex) {
      throw new WingsException(ex.getMessage());
    }
  }

  @Override
  public AppdynamicsTier getTier(String connectorId, long appdynamicsAppId, String tierId) {
    return getTier(connectorId, appdynamicsAppId, tierId, null, null,
        ThirdPartyApiCallLog.createApiCallLog(GLOBAL_ACCOUNT_ID, NO_STATE_EXECUTION_ID));
  }

  @Override
  public AppdynamicsTier getTier(String connectorId, long appdynamicsAppId, String tierId, String appId,
      String workflowExecutionId, ThirdPartyApiCallLog apiCallLog) {
    try {
      AppdynamicsTier appdynamicsTier = null;
      Set<AppdynamicsTier> tiers = getTiers(connectorId, appdynamicsAppId, appId, workflowExecutionId, apiCallLog);
      for (AppdynamicsTier tier : tiers) {
        if (String.valueOf(tier.getId()).equals(tierId)) {
          appdynamicsTier = tier;
          break;
        }
      }
      return appdynamicsTier;
    } catch (Exception ex) {
      throw new WingsException(ex.getMessage());
    }
  }

  @Override
  public String getAppDynamicsApplicationByName(
      String analysisServerConfigId, String applicationName, String appId, String workflowExecutionId) {
    try {
      String applicationId = null;
      List<NewRelicApplication> apps = getApplications(analysisServerConfigId, appId, workflowExecutionId);
      for (NewRelicApplication app : apps) {
        if (String.valueOf(app.getName()).equals(applicationName)) {
          applicationId = String.valueOf(app.getId());
          break;
        }
      }
      if (isEmpty(applicationId)) {
        throw new WingsException("Invalid AppDynamics Application Name provided : " + applicationName);
      }
      return applicationId;
    } catch (Exception ex) {
      throw new WingsException(ex.getMessage());
    }
  }

  @Override
  public String getTierByName(String analysisServerConfigId, String applicationId, String tierName, String appId,
      String workflowExecutionId, ThirdPartyApiCallLog apiCallLog) {
    try {
      String tierId = null;
      Set<AppdynamicsTier> tiers =
          getTiers(analysisServerConfigId, Long.parseLong(applicationId), appId, workflowExecutionId, apiCallLog);
      for (AppdynamicsTier tier : tiers) {
        if (String.valueOf(tier.getName()).equals(tierName)) {
          tierId = String.valueOf(tier.getId());
          break;
        }
      }
      if (isEmpty(tierId)) {
        throw new WingsException("Invalid AppDynamics Tier Name provided : " + tierName);
      }
      return tierId;
    } catch (Exception ex) {
      throw new WingsException(ex.getMessage());
    }
  }
}
