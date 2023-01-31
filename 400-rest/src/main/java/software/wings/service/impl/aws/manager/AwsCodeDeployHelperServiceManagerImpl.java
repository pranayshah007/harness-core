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
import software.wings.service.impl.aws.model.AwsCodeDeployListAppRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployListAppResponse;
import software.wings.service.impl.aws.model.AwsCodeDeployListAppRevisionRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployListAppRevisionResponse;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentConfigRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentConfigResponse;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentGroupRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentGroupResponse;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentInstancesRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentInstancesResponse;
import software.wings.service.impl.aws.model.AwsCodeDeployRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployS3LocationData;
import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.aws.manager.AwsCodeDeployHelperServiceManager;

import com.amazonaws.services.ec2.model.Instance;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
@OwnedBy(CDP)
public class AwsCodeDeployHelperServiceManagerImpl implements AwsCodeDeployHelperServiceManager {
  private static final long TIME_OUT_IN_MINUTES = 2;
  @Inject private DelegateService delegateService;
  @Inject private AwsHelperServiceManager helper;

  @Override
  public List<String> listApplications(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String appId) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsCodeDeployListAppRequest.builder()
            .awsConfig(awsConfig)
            .encryptionDetails(encryptionDetails)
            .region(region)
            .build(),
        appId);
    return ((AwsCodeDeployListAppResponse) response).getApplications();
  }

  @Override
  public List<String> listDeploymentConfiguration(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails, String region, String appId) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsCodeDeployListDeploymentConfigRequest.builder()
            .awsConfig(awsConfig)
            .encryptionDetails(encryptedDataDetails)
            .region(region)
            .build(),
        appId);
    return ((AwsCodeDeployListDeploymentConfigResponse) response).getDeploymentConfig();
  }

  @Override
  public List<String> listDeploymentGroups(AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails,
      String region, String appName, String appId) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsCodeDeployListDeploymentGroupRequest.builder()
            .awsConfig(awsConfig)
            .encryptionDetails(encryptedDataDetails)
            .region(region)
            .appName(appName)
            .build(),
        appId);
    return ((AwsCodeDeployListDeploymentGroupResponse) response).getDeploymentGroups();
  }

  @Override
  public List<Instance> listDeploymentInstances(AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails,
      String region, String deploymentId, String appId) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsCodeDeployListDeploymentInstancesRequest.builder()
            .awsConfig(awsConfig)
            .encryptionDetails(encryptedDataDetails)
            .region(region)
            .deploymentId(deploymentId)
            .build(),
        appId);
    return ((AwsCodeDeployListDeploymentInstancesResponse) response).getInstances();
  }

  @Override
  public AwsCodeDeployS3LocationData listAppRevision(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptedDataDetails, String region, String appName, String deploymentGroupName,
      String appId) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsCodeDeployListAppRevisionRequest.builder()
            .awsConfig(awsConfig)
            .encryptionDetails(encryptedDataDetails)
            .region(region)
            .appName(appName)
            .deploymentGroupName(deploymentGroupName)
            .build(),
        appId);
    return ((AwsCodeDeployListAppRevisionResponse) response).getS3LocationData();
  }

  private AwsResponse executeTask(String accountId, AwsCodeDeployRequest request, String appId) {
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, isNotEmpty(appId) ? appId : GLOBAL_APP_ID)
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.AWS_CODE_DEPLOY_TASK.name())
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
