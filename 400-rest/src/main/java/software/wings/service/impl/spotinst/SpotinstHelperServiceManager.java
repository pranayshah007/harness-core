/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.spotinst;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.spotinst.model.SpotInstConstants.defaultSyncSpotinstTimeoutMin;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.service.impl.AssignDelegateServiceImpl.SCOPE_WILDCARD;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.spotinst.request.SpotInstGetElastigroupJsonParameters;
import io.harness.delegate.task.spotinst.request.SpotInstListElastigroupInstancesParameters;
import io.harness.delegate.task.spotinst.request.SpotInstListElastigroupNamesParameters;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters;
import io.harness.delegate.task.spotinst.response.SpotInstGetElastigroupJsonResponse;
import io.harness.delegate.task.spotinst.response.SpotInstListElastigroupInstancesResponse;
import io.harness.delegate.task.spotinst.response.SpotInstListElastigroupNamesResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.spotinst.model.ElastiGroup;

import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;
import software.wings.beans.TaskType;
import software.wings.service.intfc.DelegateService;

import com.amazonaws.services.ec2.model.Instance;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class SpotinstHelperServiceManager {
  @Inject private DelegateService delegateService;

  public List<ElastiGroup> listElastigroups(
      SpotInstConfig spotInstConfig, List<EncryptedDataDetail> encryptionDetails, String appId) {
    SpotInstTaskResponse response =
        executeTask(spotInstConfig.getAccountId(), SpotInstListElastigroupNamesParameters.builder().build(),
            spotInstConfig, encryptionDetails, null, emptyList(), appId);
    return ((SpotInstListElastigroupNamesResponse) response).getElastigroups();
  }

  public String getElastigroupJson(
      SpotInstConfig spotInstConfig, List<EncryptedDataDetail> encryptionDetails, String appId, String elastigroupId) {
    SpotInstTaskResponse response = executeTask(spotInstConfig.getAccountId(),
        SpotInstGetElastigroupJsonParameters.builder().elastigroupId(elastigroupId).build(), spotInstConfig,
        encryptionDetails, null, emptyList(), appId);
    return ((SpotInstGetElastigroupJsonResponse) response).getElastigroupJson();
  }

  public List<Instance> listElastigroupInstances(SpotInstConfig spotinstConfig,
      List<EncryptedDataDetail> spotinstEncryptionDetails, AwsConfig awsConfig,
      List<EncryptedDataDetail> awsEncryptionDetails, String region, String appId, String elastigroupId) {
    SpotInstTaskResponse response = executeTask(spotinstConfig.getAccountId(),
        SpotInstListElastigroupInstancesParameters.builder().awsRegion(region).elastigroupId(elastigroupId).build(),
        spotinstConfig, spotinstEncryptionDetails, awsConfig, awsEncryptionDetails, appId);
    return ((SpotInstListElastigroupInstancesResponse) response).getElastigroupInstances();
  }

  private SpotInstTaskResponse executeTask(String accountId, SpotInstTaskParameters parameters,
      SpotInstConfig spotInstConfig, List<EncryptedDataDetail> spotinstEncryptionDetails, AwsConfig awsConfig,
      List<EncryptedDataDetail> awsEncryptionDetails, String appId) {
    List<String> tagList = null;
    if (awsConfig != null) {
      String tag = awsConfig.getTag();
      if (isNotEmpty(tag)) {
        tagList = singletonList(tag);
      }
    }
    SpotInstCommandRequest request = SpotInstCommandRequest.builder()
                                         .awsConfig(awsConfig)
                                         .awsEncryptionDetails(awsEncryptionDetails)
                                         .spotInstConfig(spotInstConfig)
                                         .spotinstEncryptionDetails(spotinstEncryptionDetails)
                                         .spotInstTaskParameters(parameters)
                                         .build();
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD,
                                        isNotEmpty(appId) && !appId.equals(GLOBAL_APP_ID) ? appId : SCOPE_WILDCARD)
                                    .tags(tagList)
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(TaskType.SPOTINST_COMMAND_TASK.name())
                                              .parameters(new Object[] {request})
                                              .timeout(TimeUnit.MINUTES.toMillis(defaultSyncSpotinstTimeoutMin))
                                              .build())
                                    .build();
    try {
      DelegateResponseData notifyResponseData = delegateService.executeTaskV2(delegateTask);
      if (notifyResponseData instanceof ErrorNotifyResponseData) {
        throw new InvalidRequestException(((ErrorNotifyResponseData) notifyResponseData).getErrorMessage(), USER);
      } else if (notifyResponseData instanceof RemoteMethodReturnValueData) {
        throw new InvalidRequestException(
            getMessage(((RemoteMethodReturnValueData) notifyResponseData).getException()), USER);
      } else if (!(notifyResponseData instanceof SpotInstTaskExecutionResponse)) {
        throw new InvalidRequestException(
            format("Unknown response from delegate: [%s]", notifyResponseData.getClass().getSimpleName()), USER);
      }
      SpotInstTaskExecutionResponse response = (SpotInstTaskExecutionResponse) notifyResponseData;
      if (FAILURE == response.getCommandExecutionStatus()) {
        throw new InvalidRequestException(response.getErrorMessage());
      }
      return response.getSpotInstTaskResponse();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new InvalidRequestException(ex.getMessage(), USER);
    }
  }
}
