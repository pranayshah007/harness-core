/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.request.CfInstanceSyncRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfInstanceSyncResponse;
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
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.InstanceDetail;

@NoArgsConstructor
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class PcfApplicationDetailsCommandTaskHandler extends PcfCommandTaskHandler {
  @Override
  public CfCommandExecutionResponse executeTaskInternal(CfCommandRequest cfCommandRequest,
      List<EncryptedDataDetail> encryptedDataDetails, ILogStreamingTaskClient logStreamingTaskClient,
      boolean isInstanceSync) {
    if (!(cfCommandRequest instanceof CfInstanceSyncRequest)) {
      throw new InvalidArgumentsException(Pair.of("cfCommandRequest", "Must be instance of CfInstanceSyncRequest"));
    }
    CfCommandExecutionResponse cfCommandExecutionResponse = CfCommandExecutionResponse.builder().build();
    CfInstanceSyncResponse cfInstanceSyncResponse =
        CfInstanceSyncResponse.builder()
            .organization(cfCommandRequest.getOrganization())
            .name(((CfInstanceSyncRequest) cfCommandRequest).getPcfApplicationName())
            .space(cfCommandRequest.getSpace())
            .build();
    cfCommandExecutionResponse.setPcfCommandResponse(cfInstanceSyncResponse);
    try {
      CfInternalConfig pcfConfig = cfCommandRequest.getPcfConfig();
      secretDecryptionService.decrypt(pcfConfig, encryptedDataDetails, isInstanceSync);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(pcfConfig, encryptedDataDetails);
      CfInstanceSyncRequest cfInstanceSyncRequest = (CfInstanceSyncRequest) cfCommandRequest;
      CfRequestConfig cfRequestConfig =
          CfRequestConfig.builder()
              .timeOutIntervalInMins(5)
              .applicationName(cfInstanceSyncRequest.getPcfApplicationName())
              .userName(String.valueOf(pcfConfig.getUsername()))
              .password(String.valueOf(pcfConfig.getPassword()))
              .endpointUrl(pcfConfig.getEndpointUrl())
              .orgName(cfCommandRequest.getOrganization())
              .spaceName(cfCommandRequest.getSpace())
              .limitPcfThreads(cfCommandRequest.isLimitPcfThreads())
              .ignorePcfConnectionContextCache(cfCommandRequest.isIgnorePcfConnectionContextCache())
              .build();

      ApplicationDetail applicationDetail = pcfDeploymentManager.getApplicationByName(cfRequestConfig);

      cfInstanceSyncResponse.setGuid(applicationDetail.getId());
      cfInstanceSyncResponse.setName(applicationDetail.getName());
      cfInstanceSyncResponse.setOrganization(cfCommandRequest.getOrganization());
      cfInstanceSyncResponse.setSpace(cfCommandRequest.getSpace());
      if (CollectionUtils.isNotEmpty(applicationDetail.getInstanceDetails())) {
        cfInstanceSyncResponse.setInstanceIndices(
            applicationDetail.getInstanceDetails().stream().map(InstanceDetail::getIndex).collect(toList()));
      }

      cfInstanceSyncResponse.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);
      cfInstanceSyncResponse.setOutput(StringUtils.EMPTY);
    } catch (Exception e) {
      log.warn("Failed while collecting PCF Application Details For Application: {}, with Error: {}",
          ((CfInstanceSyncRequest) cfCommandRequest).getPcfApplicationName(), e);
      cfInstanceSyncResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
      cfInstanceSyncResponse.setOutput(ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(e)));
    }

    cfCommandExecutionResponse.setErrorMessage(cfInstanceSyncResponse.getOutput());
    cfCommandExecutionResponse.setCommandExecutionStatus(cfInstanceSyncResponse.getCommandExecutionStatus());
    cfCommandExecutionResponse.setPcfCommandResponse(cfInstanceSyncResponse);

    return cfCommandExecutionResponse;
  }
}
