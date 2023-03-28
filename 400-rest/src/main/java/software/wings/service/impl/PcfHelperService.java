/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.LIMIT_PCF_THREADS;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.service.impl.AssignDelegateServiceImpl.SCOPE_WILDCARD;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.pcf.CfCommandRequest.PcfCommandType;
import io.harness.delegate.task.pcf.request.CfInfraMappingDataRequest;
import io.harness.delegate.task.pcf.request.CfInfraMappingDataRequest.ActionType;
import io.harness.delegate.task.pcf.request.CfInstanceSyncRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfInfraMappingDataResponse;
import io.harness.delegate.task.pcf.response.CfInstanceSyncResponse;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pcf.PcfAppNotFoundException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.PcfConfig;
import software.wings.beans.TaskType;
import software.wings.beans.infrastructure.instance.info.PcfInstanceInfo;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.mappers.artifact.CfConfigToInternalMapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * Created by bzane on 2/22/17
 */
@Singleton
@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._960_API_SERVICES)
public class PcfHelperService {
  @Inject private DelegateService delegateService;
  @Inject private SecretManager secretManager;
  @Inject private FeatureFlagService featureFlagService;

  public void validate(PcfConfig pcfConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    DelegateResponseData notifyResponseData = null;
    CfCommandExecutionResponse cfCommandExecutionResponse;

    try {
      notifyResponseData = delegateService.executeTaskV2(
          DelegateTask.builder()
              .accountId(pcfConfig.getAccountId())
              .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, SCOPE_WILDCARD)
              .data(TaskData.builder()
                        .async(false)
                        .taskType(TaskType.PCF_COMMAND_TASK.name())
                        .parameters(new Object[] {CfInfraMappingDataRequest.builder()
                                                      .pcfConfig(CfConfigToInternalMapper.toCfInternalConfig(pcfConfig))
                                                      .pcfCommandType(PcfCommandType.VALIDATE)
                                                      .limitPcfThreads(featureFlagService.isEnabled(
                                                          LIMIT_PCF_THREADS, pcfConfig.getAccountId()))
                                                      .timeoutIntervalInMin(2)
                                                      .build(),
                            encryptedDataDetails})
                        .timeout(TimeUnit.MINUTES.toMillis(2))
                        .build())
              .build());
    } catch (InterruptedException e) {
      cfCommandExecutionResponse = CfCommandExecutionResponse.builder()
                                       .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                       .errorMessage(ExceptionUtils.getMessage(e))
                                       .build();
    }

    if (notifyResponseData instanceof ErrorNotifyResponseData) {
      throw new InvalidRequestException(((ErrorNotifyResponseData) notifyResponseData).getErrorMessage(), USER);
    } else if (notifyResponseData instanceof RemoteMethodReturnValueData) {
      throw new InvalidRequestException(
          getMessage(((RemoteMethodReturnValueData) notifyResponseData).getException()), USER);
    } else if (!(notifyResponseData instanceof CfCommandExecutionResponse)) {
      throw new InvalidRequestException(
          format("Unknown response from delegate: [%s]", notifyResponseData.getClass().getSimpleName()), USER);
    }
    cfCommandExecutionResponse = (CfCommandExecutionResponse) notifyResponseData;
    if (CommandExecutionStatus.FAILURE == cfCommandExecutionResponse.getCommandExecutionStatus()) {
      log.warn(cfCommandExecutionResponse.getErrorMessage());
      throw new InvalidRequestException(cfCommandExecutionResponse.getErrorMessage());
    }
  }

  public List<PcfInstanceInfo> getApplicationDetails(String pcfApplicationName, String organization, String space,
      PcfConfig pcfConfig, CfCommandExecutionResponse pcfCommandExecutionPerpTaskResponse)
      throws PcfAppNotFoundException {
    CfCommandExecutionResponse cfCommandExecutionResponse = pcfCommandExecutionPerpTaskResponse;

    try {
      List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(pcfConfig, null, null);

      if (cfCommandExecutionResponse == null) {
        cfCommandExecutionResponse =
            executeTaskOnDelegate(pcfApplicationName, organization, space, pcfConfig, encryptionDetails);
      }

      CfInstanceSyncResponse cfInstanceSyncResponse =
          validatePcfInstanceSyncResponse(pcfApplicationName, organization, space, cfCommandExecutionResponse);

      // creates the response based on the count of instances it has got.
      if (isNotEmpty(cfInstanceSyncResponse.getInstanceIndices())) {
        return getPcfInstanceInfoList(cfInstanceSyncResponse);
      }

    } catch (InterruptedException e) {
      throw new WingsException(ErrorCode.GENERAL_ERROR).addParam("message", "Failed to fetch app details for PCF");
    }

    return Collections.emptyList();
  }

  private CfCommandExecutionResponse executeTaskOnDelegate(String pcfApplicationName, String organization, String space,
      PcfConfig pcfConfig, List<EncryptedDataDetail> encryptionDetails) throws InterruptedException {
    CfCommandExecutionResponse cfCommandExecutionResponse;
    cfCommandExecutionResponse = delegateService.executeTaskV2(
        DelegateTask.builder()
            .accountId(pcfConfig.getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.PCF_COMMAND_TASK.name())
                      .parameters(new Object[] {CfInstanceSyncRequest.builder()
                                                    .pcfConfig(CfConfigToInternalMapper.toCfInternalConfig(pcfConfig))
                                                    .pcfApplicationName(pcfApplicationName)
                                                    .organization(organization)
                                                    .space(space)
                                                    .pcfCommandType(PcfCommandType.APP_DETAILS)
                                                    .timeoutIntervalInMin(5)
                                                    .limitPcfThreads(featureFlagService.isEnabled(
                                                        LIMIT_PCF_THREADS, pcfConfig.getAccountId()))
                                                    .build(),
                          encryptionDetails})
                      .timeout(TimeUnit.MINUTES.toMillis(5))
                      .build())
            .build());
    return cfCommandExecutionResponse;
  }

  public CfInstanceSyncResponse validatePcfInstanceSyncResponse(String pcfApplicationName, String organization,
      String space, CfCommandExecutionResponse cfCommandExecutionResponse) throws PcfAppNotFoundException {
    CfInstanceSyncResponse cfInstanceSyncResponse =
        (CfInstanceSyncResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    // checks the status code and error messages.
    if (CommandExecutionStatus.FAILURE == cfCommandExecutionResponse.getCommandExecutionStatus()) {
      if (cfCommandExecutionResponse.getErrorMessage().contains(pcfApplicationName + " does not exist")
          || cfCommandExecutionResponse.getErrorMessage().contains(organization + " does not exist")
          || cfCommandExecutionResponse.getErrorMessage().contains(space + " does not exist")) {
        throw new PcfAppNotFoundException(cfCommandExecutionResponse.getErrorMessage());
      } else {
        String errMsg = format("Failed to fetch app details for PCF APP: %s with Error: %s", pcfApplicationName,
            cfInstanceSyncResponse.getOutput());
        throw new WingsException(ErrorCode.GENERAL_ERROR, errMsg).addParam("message", errMsg);
      }
    }
    return cfInstanceSyncResponse;
  }

  @NotNull
  private List<PcfInstanceInfo> getPcfInstanceInfoList(CfInstanceSyncResponse cfInstanceSyncResponse) {
    return cfInstanceSyncResponse.getInstanceIndices()
        .stream()
        .map(index
            -> PcfInstanceInfo.builder()
                   .instanceIndex(index)
                   .pcfApplicationName(cfInstanceSyncResponse.getName())
                   .pcfApplicationGuid(cfInstanceSyncResponse.getGuid())
                   .organization(cfInstanceSyncResponse.getOrganization())
                   .space(cfInstanceSyncResponse.getSpace())
                   .id(cfInstanceSyncResponse.getGuid() + ":" + index)
                   .build())
        .collect(Collectors.toList());
  }

  public List<String> listOrganizations(PcfConfig pcfConfig) {
    List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(pcfConfig, null, null);
    CfCommandExecutionResponse cfCommandExecutionResponse;
    try {
      cfCommandExecutionResponse = delegateService.executeTaskV2(
          DelegateTask.builder()
              .accountId(pcfConfig.getAccountId())
              .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
              .data(TaskData.builder()
                        .async(false)
                        .taskType(TaskType.PCF_COMMAND_TASK.name())
                        .parameters(new Object[] {CfInfraMappingDataRequest.builder()
                                                      .pcfConfig(CfConfigToInternalMapper.toCfInternalConfig(pcfConfig))
                                                      .limitPcfThreads(featureFlagService.isEnabled(
                                                          LIMIT_PCF_THREADS, pcfConfig.getAccountId()))
                                                      .pcfCommandType(PcfCommandType.DATAFETCH)
                                                      .actionType(ActionType.FETCH_ORG)
                                                      .timeoutIntervalInMin(2)
                                                      .build(),
                            encryptionDetails})
                        .timeout(TimeUnit.MINUTES.toMillis(2))
                        .build())
              .build());
    } catch (InterruptedException e) {
      cfCommandExecutionResponse = CfCommandExecutionResponse.builder()
                                       .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                       .errorMessage(ExceptionUtils.getMessage(e))
                                       .build();
    }

    if (CommandExecutionStatus.SUCCESS == cfCommandExecutionResponse.getCommandExecutionStatus()) {
      return ((CfInfraMappingDataResponse) cfCommandExecutionResponse.getPcfCommandResponse()).getOrganizations();
    } else {
      log.warn(cfCommandExecutionResponse.getErrorMessage());
      throw new InvalidRequestException(cfCommandExecutionResponse.getErrorMessage());
    }
  }

  public String createRoute(PcfConfig pcfConfig, String organization, String space, String host, String domain,
      String path, boolean tcpRoute, boolean useRandomPort, Integer port) {
    List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(pcfConfig, null, null);
    CfCommandExecutionResponse cfCommandExecutionResponse;
    try {
      cfCommandExecutionResponse = delegateService.executeTaskV2(
          DelegateTask.builder()
              .accountId(pcfConfig.getAccountId())
              .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
              .data(TaskData.builder()
                        .async(false)
                        .taskType(TaskType.PCF_COMMAND_TASK.name())
                        .parameters(new Object[] {CfInfraMappingDataRequest.builder()
                                                      .pcfConfig(CfConfigToInternalMapper.toCfInternalConfig(pcfConfig))
                                                      .pcfCommandType(PcfCommandType.CREATE_ROUTE)
                                                      .organization(organization)
                                                      .space(space)
                                                      .host(host)
                                                      .limitPcfThreads(featureFlagService.isEnabled(
                                                          LIMIT_PCF_THREADS, pcfConfig.getAccountId()))
                                                      .domain(domain)
                                                      .path(path)
                                                      .tcpRoute(tcpRoute)
                                                      .useRandomPort(useRandomPort)
                                                      .port(port)
                                                      .timeoutIntervalInMin(2)
                                                      .build(),
                            encryptionDetails})
                        .timeout(TimeUnit.MINUTES.toMillis(2))
                        .build())
              .build());
    } catch (InterruptedException e) {
      cfCommandExecutionResponse = CfCommandExecutionResponse.builder()
                                       .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                       .errorMessage(ExceptionUtils.getMessage(e))
                                       .build();
    }

    if (CommandExecutionStatus.SUCCESS == cfCommandExecutionResponse.getCommandExecutionStatus()) {
      return ((CfInfraMappingDataResponse) cfCommandExecutionResponse.getPcfCommandResponse()).getRouteMaps().get(0);
    } else {
      log.warn(cfCommandExecutionResponse.getErrorMessage());
      throw new InvalidRequestException(cfCommandExecutionResponse.getErrorMessage());
    }
  }

  public List<String> listSpaces(PcfConfig pcfConfig, String organization) {
    List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(pcfConfig, null, null);
    CfCommandExecutionResponse cfCommandExecutionResponse;
    try {
      cfCommandExecutionResponse = delegateService.executeTaskV2(
          DelegateTask.builder()
              .accountId(pcfConfig.getAccountId())
              .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
              .data(TaskData.builder()
                        .async(false)
                        .taskType(TaskType.PCF_COMMAND_TASK.name())
                        .parameters(new Object[] {CfInfraMappingDataRequest.builder()
                                                      .pcfConfig(CfConfigToInternalMapper.toCfInternalConfig(pcfConfig))
                                                      .organization(organization)
                                                      .limitPcfThreads(featureFlagService.isEnabled(
                                                          LIMIT_PCF_THREADS, pcfConfig.getAccountId()))
                                                      .pcfCommandType(PcfCommandType.DATAFETCH)
                                                      .actionType(ActionType.FETCH_SPACE)
                                                      .timeoutIntervalInMin(2)
                                                      .build(),
                            encryptionDetails})
                        .timeout(TimeUnit.MINUTES.toMillis(2))
                        .build())
              .build());
    } catch (InterruptedException e) {
      cfCommandExecutionResponse = CfCommandExecutionResponse.builder()
                                       .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                       .errorMessage(ExceptionUtils.getMessage(e))
                                       .build();
    }

    if (CommandExecutionStatus.SUCCESS == cfCommandExecutionResponse.getCommandExecutionStatus()) {
      return ((CfInfraMappingDataResponse) cfCommandExecutionResponse.getPcfCommandResponse()).getSpaces();
    } else {
      log.warn(cfCommandExecutionResponse.getErrorMessage());
      throw new InvalidRequestException(cfCommandExecutionResponse.getErrorMessage());
    }
  }

  public List<String> listRoutes(PcfConfig pcfConfig, String organization, String space) {
    List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(pcfConfig, null, null);
    CfCommandExecutionResponse cfCommandExecutionResponse;
    try {
      cfCommandExecutionResponse = delegateService.executeTaskV2(
          DelegateTask.builder()
              .accountId(pcfConfig.getAccountId())
              .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
              .data(TaskData.builder()
                        .async(false)
                        .taskType(TaskType.PCF_COMMAND_TASK.name())
                        .parameters(new Object[] {CfInfraMappingDataRequest.builder()
                                                      .pcfConfig(CfConfigToInternalMapper.toCfInternalConfig(pcfConfig))
                                                      .organization(organization)
                                                      .space(space)
                                                      .limitPcfThreads(featureFlagService.isEnabled(
                                                          LIMIT_PCF_THREADS, pcfConfig.getAccountId()))
                                                      .pcfCommandType(PcfCommandType.DATAFETCH)
                                                      .actionType(ActionType.FETCH_ROUTE)
                                                      .timeoutIntervalInMin(2)
                                                      .build(),
                            encryptionDetails})
                        .timeout(TimeUnit.MINUTES.toMillis(2))
                        .build())
              .build());
    } catch (InterruptedException e) {
      cfCommandExecutionResponse = CfCommandExecutionResponse.builder()
                                       .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                       .errorMessage(ExceptionUtils.getMessage(e))
                                       .build();
    }

    if (CommandExecutionStatus.SUCCESS == cfCommandExecutionResponse.getCommandExecutionStatus()) {
      return ((CfInfraMappingDataResponse) cfCommandExecutionResponse.getPcfCommandResponse()).getRouteMaps();
    } else {
      log.warn(cfCommandExecutionResponse.getErrorMessage());
      throw new InvalidRequestException(cfCommandExecutionResponse.getErrorMessage());
    }
  }

  public Integer getRunningInstanceCount(PcfConfig pcfConfig, String organization, String space, String appPrefix) {
    List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(pcfConfig, null, null);
    CfCommandExecutionResponse cfCommandExecutionResponse;
    try {
      cfCommandExecutionResponse = delegateService.executeTaskV2(
          DelegateTask.builder()
              .accountId(pcfConfig.getAccountId())
              .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
              .data(TaskData.builder()
                        .async(false)
                        .taskType(TaskType.PCF_COMMAND_TASK.name())
                        .parameters(new Object[] {CfInfraMappingDataRequest.builder()
                                                      .pcfConfig(CfConfigToInternalMapper.toCfInternalConfig(pcfConfig))
                                                      .organization(organization)
                                                      .space(space)
                                                      .actionType(ActionType.RUNNING_COUNT)
                                                      .limitPcfThreads(featureFlagService.isEnabled(
                                                          LIMIT_PCF_THREADS, pcfConfig.getAccountId()))
                                                      .pcfCommandType(PcfCommandType.DATAFETCH)
                                                      .applicationNamePrefix(appPrefix)
                                                      .timeoutIntervalInMin(2)
                                                      .build(),
                            encryptionDetails})
                        .timeout(TimeUnit.MINUTES.toMillis(2))
                        .build())
              .build());
    } catch (InterruptedException e) {
      cfCommandExecutionResponse = CfCommandExecutionResponse.builder()
                                       .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                       .errorMessage(ExceptionUtils.getMessage(e))
                                       .build();
    }

    if (CommandExecutionStatus.SUCCESS == cfCommandExecutionResponse.getCommandExecutionStatus()) {
      CfInfraMappingDataResponse pcfInfraMappingDataResponse =
          (CfInfraMappingDataResponse) cfCommandExecutionResponse.getPcfCommandResponse();
      return pcfInfraMappingDataResponse.getRunningInstanceCount();
    } else {
      log.warn(cfCommandExecutionResponse.getErrorMessage());
      throw new InvalidRequestException(cfCommandExecutionResponse.getErrorMessage());
    }
  }

  public int getInstanceCount(CfCommandExecutionResponse cfCommandExecutionResponse) {
    CfInstanceSyncResponse cfInstanceSyncResponse =
        (CfInstanceSyncResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    return emptyIfNull(cfInstanceSyncResponse.getInstanceIndices()).size();
  }
}
