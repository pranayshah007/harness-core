/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Collections.emptyList;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.request.CfInfraMappingDataRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfInfraMappingDataResponse;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Singleton;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudfoundry.operations.applications.ApplicationSummary;

@NoArgsConstructor
@Singleton
@Slf4j
@OwnedBy(CDP)
public class PcfDataFetchCommandTaskHandler extends PcfCommandTaskHandler {
  /**
   * Fetches Organization, Spaces, RouteMap data
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
    CfInfraMappingDataResponse cfInfraMappingDataResponse = CfInfraMappingDataResponse.builder().build();
    cfCommandExecutionResponse.setPcfCommandResponse(cfInfraMappingDataResponse);

    try {
      switch (cfInfraMappingDataRequest.getActionType()) {
        case FETCH_ORG:
          getOrgs(pcfDeploymentManager, cfInfraMappingDataRequest, cfInfraMappingDataResponse, pcfConfig);
          break;

        case FETCH_SPACE:
          getSpaces(pcfDeploymentManager, cfInfraMappingDataRequest, cfInfraMappingDataResponse, pcfConfig);
          break;

        case FETCH_ROUTE:
          getRoutes(pcfDeploymentManager, cfInfraMappingDataRequest, cfInfraMappingDataResponse, pcfConfig);
          break;

        case RUNNING_COUNT:
          getRunningCount(pcfDeploymentManager, cfInfraMappingDataRequest, cfInfraMappingDataResponse, pcfConfig);
          break;

        default:
          throw new WingsException(
              ErrorCode.INVALID_ARGUMENT, "Invalid ActionType: " + cfInfraMappingDataRequest.getActionType())
              .addParam("message", "Invalid ActionType: " + cfInfraMappingDataRequest.getActionType());
      }

      cfInfraMappingDataResponse.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);
      cfInfraMappingDataResponse.setOutput(StringUtils.EMPTY);
    } catch (Exception e) {
      log.error("Exception in processing PCF DataFetch task", e);
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

  private void getRunningCount(CfDeploymentManager pcfDeploymentManager,
      CfInfraMappingDataRequest cfInfraMappingDataRequest, CfInfraMappingDataResponse cfInfraMappingDataResponse,
      CfInternalConfig pcfConfig) throws PivotalClientApiException {
    Integer count = Integer.valueOf(0);

    List<ApplicationSummary> applicationSummaries =
        pcfDeploymentManager.getPreviousReleases(getPcfRequestConfig(cfInfraMappingDataRequest, pcfConfig),
            cfInfraMappingDataRequest.getApplicationNamePrefix());

    applicationSummaries = applicationSummaries.stream()
                               .filter(applicationSummary
                                   -> applicationSummary.getRunningInstances() > 0
                                       && !"STOPPED".equals(applicationSummary.getRequestedState()))
                               .collect(toList());

    applicationSummaries =
        applicationSummaries.stream()
            .sorted(comparingInt(applicationSummary
                -> pcfCommandTaskBaseHelper.getRevisionFromReleaseName(applicationSummary.getName())))
            .collect(toList());

    if (isNotEmpty(applicationSummaries)) {
      count = applicationSummaries.get(applicationSummaries.size() - 1).getRunningInstances();
    }

    cfInfraMappingDataResponse.setRunningInstanceCount(count);
  }

  private void getRoutes(CfDeploymentManager pcfDeploymentManager, CfInfraMappingDataRequest cfInfraMappingDataRequest,
      CfInfraMappingDataResponse cfInfraMappingDataResponse, CfInternalConfig pcfConfig)
      throws PivotalClientApiException {
    List<String> routes = pcfDeploymentManager.getRouteMaps(getPcfRequestConfig(cfInfraMappingDataRequest, pcfConfig));

    cfInfraMappingDataResponse.setRouteMaps(routes);
  }

  private void getSpaces(CfDeploymentManager pcfDeploymentManager, CfInfraMappingDataRequest cfInfraMappingDataRequest,
      CfInfraMappingDataResponse cfInfraMappingDataResponse, CfInternalConfig pcfConfig)
      throws PivotalClientApiException {
    List<String> spaces =
        pcfDeploymentManager.getSpacesForOrganization(getPcfRequestConfig(cfInfraMappingDataRequest, pcfConfig));

    cfInfraMappingDataResponse.setSpaces(spaces);
  }

  private void getOrgs(CfDeploymentManager pcfDeploymentManager, CfInfraMappingDataRequest cfInfraMappingDataRequest,
      CfInfraMappingDataResponse cfInfraMappingDataResponse, CfInternalConfig pcfConfig)
      throws PivotalClientApiException {
    List<String> orgs =
        pcfDeploymentManager.getOrganizations(getPcfRequestConfig(cfInfraMappingDataRequest, pcfConfig));

    cfInfraMappingDataResponse.setOrganizations(orgs);
  }

  private CfRequestConfig getPcfRequestConfig(
      CfInfraMappingDataRequest cfInfraMappingDataRequest, CfInternalConfig pcfConfig) {
    return CfRequestConfig.builder()
        .endpointUrl(pcfConfig.getEndpointUrl())
        .limitPcfThreads(cfInfraMappingDataRequest.isLimitPcfThreads())
        .ignorePcfConnectionContextCache(cfInfraMappingDataRequest.isIgnorePcfConnectionContextCache())
        .orgName(cfInfraMappingDataRequest.getOrganization())
        .spaceName(cfInfraMappingDataRequest.getSpace())
        .userName(String.valueOf(pcfConfig.getUsername()))
        .password(String.valueOf(pcfConfig.getPassword()))
        .timeOutIntervalInMins(cfInfraMappingDataRequest.getTimeoutIntervalInMin())
        .build();
  }
}
