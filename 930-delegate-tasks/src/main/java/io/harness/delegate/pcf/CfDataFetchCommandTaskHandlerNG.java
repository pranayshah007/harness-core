/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.delegate.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Collections.emptyList;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfInfraMappingDataResult;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.delegate.task.pcf.request.CfCommandRequestNG;
import io.harness.delegate.task.pcf.request.CfInfraMappingDataRequestNG;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.CfInfraMappingDataResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CloudFoundryConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudfoundry.operations.applications.ApplicationSummary;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@NoArgsConstructor
@Singleton
@Slf4j
@OwnedBy(CDP)
public class CfDataFetchCommandTaskHandlerNG extends CfCommandTaskNGHandler {
  @Inject TasNgConfigMapper tasNgConfigMapper;
  @Inject CfDeploymentManager cfDeploymentManager;
  @Inject protected PcfCommandTaskBaseHelper pcfCommandTaskBaseHelper;
  @Override
  protected CfCommandResponseNG executeTaskInternal(CfCommandRequestNG cfCommandRequestNG,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(cfCommandRequestNG instanceof CfInfraMappingDataRequestNG)) {
      throw new InvalidArgumentsException(
          Pair.of("CfCommandRequest", "Must be instance of CfInfraMappingDataRequestNG"));
    }
    CfInfraMappingDataRequestNG cfInfraMappingDataRequestNG = (CfInfraMappingDataRequestNG) cfCommandRequestNG;
    TasInfraConfig tasInfraConfig = cfInfraMappingDataRequestNG.getTasInfraConfig();
    CloudFoundryConfig cfConfig = tasNgConfigMapper.mapTasConfigWithDecryption(
        tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());
    CfInfraMappingDataResponseNG cfInfraMappingDataResponseNG = CfInfraMappingDataResponseNG.builder().build();
    CfInfraMappingDataResult cfInfraMappingDataResult = CfInfraMappingDataResult.builder().build();
    try {
      switch (cfInfraMappingDataRequestNG.getActionType()) {
        case FETCH_ORG:
          getOrgs(cfDeploymentManager, cfInfraMappingDataRequestNG, cfInfraMappingDataResponseNG, cfConfig);
          break;

        case FETCH_SPACE:
          getSpaces(cfDeploymentManager, cfInfraMappingDataRequestNG, cfInfraMappingDataResponseNG, cfConfig);
          break;

        case FETCH_ROUTE:
          getRoutes(cfDeploymentManager, cfInfraMappingDataRequestNG, cfInfraMappingDataResponseNG, cfConfig);
          break;

        case RUNNING_COUNT:
          getRunningCount(cfDeploymentManager, cfInfraMappingDataRequestNG, cfInfraMappingDataResponseNG, cfConfig);
          break;

        default:
          throw new WingsException(
              ErrorCode.INVALID_ARGUMENT, "Invalid ActionType: " + cfInfraMappingDataRequestNG.getActionType())
              .addParam("message", "Invalid ActionType: " + cfInfraMappingDataRequestNG.getActionType());
      }

      cfInfraMappingDataResponseNG.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in processing CF DataFetch task", sanitizedException);
      cfInfraMappingDataResult.setOrganizations(emptyList());
      cfInfraMappingDataResult.setSpaces(emptyList());
      cfInfraMappingDataResult.setRouteMaps(emptyList());
      cfInfraMappingDataResponseNG.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
      cfInfraMappingDataResponseNG.setErrorMessage(
          ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(e)));
      cfInfraMappingDataResponseNG.setCfInfraMappingDataResult(cfInfraMappingDataResult);
    }
    return cfInfraMappingDataResponseNG;
  }

  private void getRoutes(CfDeploymentManager cfDeploymentManager,
      CfInfraMappingDataRequestNG cfInfraMappingDataRequestNG,
      CfInfraMappingDataResponseNG cfInfraMappingDataResponseNG, CloudFoundryConfig pcfConfig)
      throws PivotalClientApiException {
    List<String> routes = cfDeploymentManager.getRouteMaps(getPcfRequestConfig(cfInfraMappingDataRequestNG, pcfConfig));

    cfInfraMappingDataResponseNG.setCfInfraMappingDataResult(
        CfInfraMappingDataResult.builder().routeMaps(routes).build());
  }

  private void getRunningCount(CfDeploymentManager cfDeploymentManager,
      CfInfraMappingDataRequestNG cfInfraMappingDataRequestNG,
      CfInfraMappingDataResponseNG cfInfraMappingDataResponseNG, CloudFoundryConfig pcfConfig)
      throws PivotalClientApiException {
    Integer count = 0;

    List<ApplicationSummary> applicationSummaries =
        cfDeploymentManager.getPreviousReleases(getPcfRequestConfig(cfInfraMappingDataRequestNG, pcfConfig),
            cfInfraMappingDataRequestNG.getApplicationNamePrefix());

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
    cfInfraMappingDataResponseNG.setCfInfraMappingDataResult(
        CfInfraMappingDataResult.builder().runningInstanceCount(count).build());
  }

  private void getSpaces(CfDeploymentManager cfDeploymentManager,
      CfInfraMappingDataRequestNG cfInfraMappingDataRequestNG,
      CfInfraMappingDataResponseNG cfInfraMappingDataResponseNG, CloudFoundryConfig pcfConfig)
      throws PivotalClientApiException {
    List<String> spaces =
        cfDeploymentManager.getSpacesForOrganization(getPcfRequestConfig(cfInfraMappingDataRequestNG, pcfConfig));
    cfInfraMappingDataResponseNG.setCfInfraMappingDataResult(CfInfraMappingDataResult.builder().spaces(spaces).build());
  }

  private void getOrgs(CfDeploymentManager cfDeploymentManager, CfInfraMappingDataRequestNG cfInfraMappingDataRequestNG,
      CfInfraMappingDataResponseNG cfInfraMappingDataResponseNG, CloudFoundryConfig pcfConfig)
      throws PivotalClientApiException {
    List<String> orgs =
        cfDeploymentManager.getOrganizations(getPcfRequestConfig(cfInfraMappingDataRequestNG, pcfConfig));
    cfInfraMappingDataResponseNG.setCfInfraMappingDataResult(
        CfInfraMappingDataResult.builder().organizations(orgs).build());
  }

  private CfRequestConfig getPcfRequestConfig(
      CfInfraMappingDataRequestNG cfInfraMappingDataRequest, CloudFoundryConfig cfConfig) {
    return CfRequestConfig.builder()
        .endpointUrl(cfConfig.getEndpointUrl())
        .orgName(cfInfraMappingDataRequest.getTasInfraConfig().getOrganization())
        .spaceName(cfInfraMappingDataRequest.getTasInfraConfig().getSpace())
        .userName(String.valueOf(cfConfig.getUserName()))
        .password(String.valueOf(cfConfig.getPassword()))
        .timeOutIntervalInMins(cfInfraMappingDataRequest.getTimeoutIntervalInMin())
        .build();
  }
}
