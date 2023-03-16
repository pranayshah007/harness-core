/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.manager;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.service.impl.AssignDelegateServiceImpl.SCOPE_WILDCARD;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.defaultString;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.TaskType;
import software.wings.service.impl.aws.model.AwsEc2ListInstancesRequest;
import software.wings.service.impl.aws.model.AwsEc2ListInstancesResponse;
import software.wings.service.impl.aws.model.AwsEc2ListRegionsRequest;
import software.wings.service.impl.aws.model.AwsEc2ListRegionsResponse;
import software.wings.service.impl.aws.model.AwsEc2ListSGsRequest;
import software.wings.service.impl.aws.model.AwsEc2ListSGsResponse;
import software.wings.service.impl.aws.model.AwsEc2ListSubnetsRequest;
import software.wings.service.impl.aws.model.AwsEc2ListSubnetsResponse;
import software.wings.service.impl.aws.model.AwsEc2ListTagsRequest;
import software.wings.service.impl.aws.model.AwsEc2ListTagsResponse;
import software.wings.service.impl.aws.model.AwsEc2ListVpcsRequest;
import software.wings.service.impl.aws.model.AwsEc2ListVpcsResponse;
import software.wings.service.impl.aws.model.AwsEc2Request;
import software.wings.service.impl.aws.model.AwsEc2ValidateCredentialsRequest;
import software.wings.service.impl.aws.model.AwsEc2ValidateCredentialsResponse;
import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.impl.aws.model.AwsSecurityGroup;
import software.wings.service.impl.aws.model.AwsSubnet;
import software.wings.service.impl.aws.model.AwsVPC;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.aws.manager.AwsEc2HelperServiceManager;

import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.ResourceType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class AwsEc2HelperServiceManagerImpl implements AwsEc2HelperServiceManager {
  private static final long TIME_OUT_IN_MINUTES = 2;
  @Inject private DelegateService delegateService;
  @Inject private AwsHelperServiceManager helper;

  @Override
  public void validateAwsAccountCredential(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsEc2ValidateCredentialsRequest.builder().awsConfig(awsConfig).encryptionDetails(encryptionDetails).build(),
        "");
    if (!((AwsEc2ValidateCredentialsResponse) response).isValid()) {
      throw new WingsException(ErrorCode.INVALID_CLOUD_PROVIDER)
          .addParam("message", defaultString(response.getErrorMessage(), "Invalid AWS credentials."));
    }
  }

  @Override
  public List<String> listRegions(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String appId) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsEc2ListRegionsRequest.builder().awsConfig(awsConfig).encryptionDetails(encryptionDetails).build(), appId);
    return ((AwsEc2ListRegionsResponse) response).getRegions();
  }

  @Override
  public List<AwsVPC> listVPCs(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String appId) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsEc2ListVpcsRequest.builder()
            .awsConfig(awsConfig)
            .encryptionDetails(encryptionDetails)
            .region(region)
            .build(),
        appId);
    return ((AwsEc2ListVpcsResponse) response).getVpcs();
  }

  @Override
  public List<AwsSubnet> listSubnets(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      List<String> vpcIds, String appId) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsEc2ListSubnetsRequest.builder()
            .awsConfig(awsConfig)
            .encryptionDetails(encryptionDetails)
            .vpcIds(vpcIds)
            .region(region)
            .build(),
        appId);
    return ((AwsEc2ListSubnetsResponse) response).getSubnets();
  }

  @Override
  public List<AwsSecurityGroup> listSGs(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      List<String> vpcIds, String appId) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsEc2ListSGsRequest.builder()
            .awsConfig(awsConfig)
            .encryptionDetails(encryptionDetails)
            .vpcIds(vpcIds)
            .region(region)
            .build(),
        appId);
    return ((AwsEc2ListSGsResponse) response).getSecurityGroups();
  }

  @Override
  public Set<String> listTags(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      String appId, ResourceType resourceType) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsEc2ListTagsRequest.builder()
            .awsConfig(awsConfig)
            .encryptionDetails(encryptionDetails)
            .region(region)
            .resourceType(resourceType.toString())
            .build(),
        appId);
    return ((AwsEc2ListTagsResponse) response).getTags();
  }

  @Override
  public Set<String> listTags(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, ResourceType resourceType) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsEc2ListTagsRequest.builder()
            .awsConfig(awsConfig)
            .encryptionDetails(encryptionDetails)
            .region(region)
            .resourceType(resourceType.toString())
            .build(),
        GLOBAL_APP_ID);
    return ((AwsEc2ListTagsResponse) response).getTags();
  }

  @Override
  public Set<String> listTags(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String appId) {
    return listTags(awsConfig, encryptionDetails, region, appId, ResourceType.Instance);
  }

  @Override
  public List<Instance> listEc2Instances(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, List<Filter> filters, String appId) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsEc2ListInstancesRequest.builder()
            .awsConfig(awsConfig)
            .encryptionDetails(encryptionDetails)
            .region(region)
            .filters(filters)
            .build(),
        appId);
    return ((AwsEc2ListInstancesResponse) response).getInstances();
  }

  private AwsResponse executeTask(String accountId, AwsEc2Request request, String appId) {
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(
                Cd1SetupFields.APP_ID_FIELD, isNotEmpty(appId) && !appId.equals(GLOBAL_APP_ID) ? appId : SCOPE_WILDCARD)
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.AWS_EC2_TASK.name())
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
      throw new InvalidRequestException(ex.getMessage(), USER);
    }
  }
}
