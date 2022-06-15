/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.request.CfInfraMappingDataRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Singleton;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@NoArgsConstructor
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class PcfValidationCommandTaskHandler extends PcfCommandTaskHandler {
  /**
   * Performs validation of PCF config while adding PCF cloud provider
   *
   * @param cfCommandRequest
   * @param encryptedDataDetails
   * @param logStreamingTaskClient
   * @param isInstanceSync
   * @return
   */
  @Override
  public CfCommandExecutionResponse executeTaskInternal(CfCommandRequest cfCommandRequest,
      List<EncryptedDataDetail> encryptedDataDetails, ILogStreamingTaskClient logStreamingTaskClient,
      boolean isInstanceSync) {
    if (!(cfCommandRequest instanceof CfInfraMappingDataRequest)) {
      throw new InvalidArgumentsException(Pair.of("cfCommandRequest", "Must be instance of CfInfraMappingDataRequest"));
    }
    CfInfraMappingDataRequest cfInfraMappingDataRequest = (CfInfraMappingDataRequest) cfCommandRequest;
    CfInternalConfig pcfConfig = cfInfraMappingDataRequest.getPcfConfig();
    secretDecryptionService.decrypt(pcfConfig, encryptedDataDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(pcfConfig, encryptedDataDetails);

    CfCommandExecutionResponse cfCommandExecutionResponse = CfCommandExecutionResponse.builder().build();
    try {
      pcfDeploymentManager.getOrganizations(
          CfRequestConfig.builder()
              .orgName(cfInfraMappingDataRequest.getOrganization())
              .userName(String.valueOf(pcfConfig.getUsername()))
              .password(String.valueOf(pcfConfig.getPassword()))
              .endpointUrl(pcfConfig.getEndpointUrl())
              .limitPcfThreads(cfInfraMappingDataRequest.isLimitPcfThreads())
              .ignorePcfConnectionContextCache(cfInfraMappingDataRequest.isIgnorePcfConnectionContextCache())
              .timeOutIntervalInMins(cfInfraMappingDataRequest.getTimeoutIntervalInMin())
              .build());

      cfCommandExecutionResponse.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);

    } catch (Exception e) {
      log.error("Exception in processing PCF validation task for Account {} ",
          cfInfraMappingDataRequest.getPcfConfig().getAccountId(), e);
      cfCommandExecutionResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
      cfCommandExecutionResponse.setErrorMessage(
          ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(e)));
    }

    return cfCommandExecutionResponse;
  }
}
