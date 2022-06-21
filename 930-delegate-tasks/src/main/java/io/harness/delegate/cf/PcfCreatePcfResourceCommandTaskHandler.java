/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.CfCommandRequest.PcfCommandType;
import io.harness.delegate.task.pcf.request.CfInfraMappingDataRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfInfraMappingDataResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@NoArgsConstructor
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class PcfCreatePcfResourceCommandTaskHandler extends PcfCommandTaskHandler {
  /**
   * Fetches Organization, Spaces, RouteMap data
   */
  @Override
  public CfCommandExecutionResponse executeTaskInternal(CfCommandRequest cfCommandRequest,
      List<EncryptedDataDetail> encryptedDataDetails, ILogStreamingTaskClient logStreamingTaskClient,
      boolean isInstanceSync) {
    if (!(cfCommandRequest instanceof CfInfraMappingDataRequest)) {
      throw new InvalidArgumentsException(Pair.of("CfCommandRequest", "Must be instance of CfInfraMappingDataRequest"));
    }
    CfInfraMappingDataRequest cfInfraMappingDataRequest = (CfInfraMappingDataRequest) cfCommandRequest;
    CfInternalConfig pcfConfig = cfInfraMappingDataRequest.getPcfConfig();
    secretDecryptionService.decrypt(pcfConfig, encryptedDataDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(pcfConfig, encryptedDataDetails);

    CfCommandExecutionResponse cfCommandExecutionResponse = CfCommandExecutionResponse.builder().build();
    CfInfraMappingDataResponse cfInfraMappingDataResponse = CfInfraMappingDataResponse.builder().build();
    cfCommandExecutionResponse.setPcfCommandResponse(cfInfraMappingDataResponse);

    try {
      if (PcfCommandType.CREATE_ROUTE == cfInfraMappingDataRequest.getPcfCommandType()) {
        String routeCreated = pcfDeploymentManager.createRouteMap(
            CfRequestConfig.builder()
                .orgName(cfInfraMappingDataRequest.getOrganization())
                .spaceName(cfInfraMappingDataRequest.getSpace())
                .userName(String.valueOf(pcfConfig.getUsername()))
                .password(String.valueOf(pcfConfig.getPassword()))
                .endpointUrl(pcfConfig.getEndpointUrl())
                .timeOutIntervalInMins(cfInfraMappingDataRequest.getTimeoutIntervalInMin())
                .limitPcfThreads(cfInfraMappingDataRequest.isLimitPcfThreads())
                .ignorePcfConnectionContextCache(cfInfraMappingDataRequest.isIgnorePcfConnectionContextCache())
                .build(),
            cfInfraMappingDataRequest.getHost(), cfInfraMappingDataRequest.getDomain(),
            cfInfraMappingDataRequest.getPath(), cfInfraMappingDataRequest.isTcpRoute(),
            cfInfraMappingDataRequest.isUseRandomPort(), cfInfraMappingDataRequest.getPort());

        cfInfraMappingDataResponse.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);
        cfInfraMappingDataResponse.setOutput(StringUtils.EMPTY);
        cfInfraMappingDataResponse.setRouteMaps(Arrays.asList(routeCreated));
      }

    } catch (Exception e) {
      log.error("Exception in processing Create Route task", e);
      cfInfraMappingDataResponse.setOrganizations(emptyList());
      cfInfraMappingDataResponse.setSpaces(emptyList());
      cfInfraMappingDataResponse.setRouteMaps(emptyList());
      cfInfraMappingDataResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
      cfInfraMappingDataResponse.setOutput(ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(e)));
    }

    cfCommandExecutionResponse.setCommandExecutionStatus(cfInfraMappingDataResponse.getCommandExecutionStatus());
    cfCommandExecutionResponse.setErrorMessage(cfInfraMappingDataResponse.getOutput());
    return cfCommandExecutionResponse;
  }
}
