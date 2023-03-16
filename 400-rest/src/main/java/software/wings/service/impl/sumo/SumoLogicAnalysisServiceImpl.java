/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.sumo;

import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.dto.ThirdPartyApiCallLog.createApiCallLog;

import io.harness.eraro.ErrorCode;
import io.harness.exception.VerificationOperationException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SumoConfig;
import software.wings.beans.SyncTaskContext;
import software.wings.service.impl.analysis.AnalysisServiceImpl;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.apm.MLServiceUtils;
import software.wings.service.intfc.sumo.SumoDelegateService;
import software.wings.service.intfc.sumo.SumoLogicAnalysisService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

/**
 * Created by Pranjal on 08/23/2018
 */
@Singleton
public class SumoLogicAnalysisServiceImpl extends AnalysisServiceImpl implements SumoLogicAnalysisService {
  @Inject private MLServiceUtils mlServiceUtils;
  @Override
  public VerificationNodeDataSetupResponse getLogDataByHost(
      String accountId, SumoLogicSetupTestNodedata sumoLogicSetupTestNodedata) {
    final SettingAttribute settingAttribute = settingsService.get(sumoLogicSetupTestNodedata.getSettingId());
    if (settingAttribute == null) {
      throw new WingsException("No setting with id: " + sumoLogicSetupTestNodedata.getSettingId() + " found");
    }
    List<EncryptedDataDetail> encryptedDataDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
    SyncTaskContext sumoTaskContext = SyncTaskContext.builder()
                                          .accountId(accountId)
                                          .appId(GLOBAL_APP_ID)
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT * 3)
                                          .build();
    try {
      return delegateProxyFactory.getV2(SumoDelegateService.class, sumoTaskContext)
          .getLogDataByHost(accountId, (SumoConfig) settingAttribute.getValue(), sumoLogicSetupTestNodedata.getQuery(),
              sumoLogicSetupTestNodedata.getHostNameField(), mlServiceUtils.getHostName(sumoLogicSetupTestNodedata),
              encryptedDataDetails,
              createApiCallLog(settingAttribute.getAccountId(), sumoLogicSetupTestNodedata.getGuid()));
    } catch (Exception e) {
      throw new VerificationOperationException(ErrorCode.SUMO_DATA_COLLECTION_ERROR, e.getMessage(), e);
    }
  }
}
