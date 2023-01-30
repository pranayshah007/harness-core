/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.manager;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.TaskType;
import software.wings.service.impl.aws.model.AwsAsgGetRunningCountData;
import software.wings.service.impl.aws.model.AwsAsgGetRunningCountRequest;
import software.wings.service.impl.aws.model.AwsAsgGetRunningCountResponse;
import software.wings.service.impl.aws.model.AwsAsgListAllNamesRequest;
import software.wings.service.impl.aws.model.AwsAsgListAllNamesResponse;
import software.wings.service.impl.aws.model.AwsAsgListDesiredCapacitiesRequest;
import software.wings.service.impl.aws.model.AwsAsgListDesiredCapacitiesResponse;
import software.wings.service.impl.aws.model.AwsAsgListInstancesRequest;
import software.wings.service.impl.aws.model.AwsAsgListInstancesResponse;
import software.wings.service.impl.aws.model.AwsAsgRequest;
import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.aws.manager.AwsAsgHelperServiceManager;

import com.amazonaws.services.ec2.model.Instance;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class AwsAsgHelperServiceManagerImpl implements AwsAsgHelperServiceManager {
  private static final long TIME_OUT_IN_MINUTES = 2;
  @Inject private DelegateService delegateService;
  @Inject private AwsHelperServiceManager helper;

  @Override
  public List<String> listAutoScalingGroupNames(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String appId) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsAsgListAllNamesRequest.builder()
            .awsConfig(awsConfig)
            .encryptionDetails(encryptionDetails)
            .region(region)
            .build(),
        appId);
    return ((AwsAsgListAllNamesResponse) response).getASgNames();
  }

  @Override
  public List<Instance> listAutoScalingGroupInstances(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String autoScalingGroupName, String appId) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsAsgListInstancesRequest.builder()
            .awsConfig(awsConfig)
            .encryptionDetails(encryptionDetails)
            .region(region)
            .autoScalingGroupName(autoScalingGroupName)
            .build(),
        appId);
    return ((AwsAsgListInstancesResponse) response).getInstances();
  }

  @Override
  public Map<String, Integer> getDesiredCapacitiesOfAsgs(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, List<String> asgs, String appId) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsAsgListDesiredCapacitiesRequest.builder()
            .awsConfig(awsConfig)
            .encryptionDetails(encryptionDetails)
            .region(region)
            .asgs(asgs)
            .build(),
        appId);
    return ((AwsAsgListDesiredCapacitiesResponse) response).getCapacities();
  }

  @Override
  public AwsAsgGetRunningCountData getCurrentlyRunningInstanceCount(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, String infraMappingId, String appId) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsAsgGetRunningCountRequest.builder()
            .awsConfig(awsConfig)
            .encryptionDetails(encryptionDetails)
            .region(region)
            .infraMappingId(infraMappingId)
            .build(),
        appId);
    return ((AwsAsgGetRunningCountResponse) response).getData();
  }

  private AwsResponse executeTask(String accountId, AwsAsgRequest request, String appId) {
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, isNotEmpty(appId) ? appId : GLOBAL_APP_ID)
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.AWS_ASG_TASK.name())
                      .parameters(new Object[] {request})
                      .timeout(TimeUnit.MINUTES.toMillis(TIME_OUT_IN_MINUTES))
                      .build())
            .build();
    try {
      DelegateResponseData notifyResponseData = delegateService.executeTaskV2(delegateTask);
      helper.validateDelegateSuccessForSyncTask(notifyResponseData);
      return (AwsResponse) notifyResponseData;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new InvalidRequestException(ex.getMessage(), WingsException.USER);
    }
  }
}
