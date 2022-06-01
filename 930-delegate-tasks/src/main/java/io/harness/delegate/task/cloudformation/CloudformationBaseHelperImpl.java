/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cloudformation;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.stream.Collectors.toMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AWSCloudformationClient;
import io.harness.aws.AwsCloudformationPrintHelper;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.cf.DeployStackRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.cloudformation.response.ExistingStackInfo;
import software.wings.helpers.ext.cloudformation.response.ExistingStackInfo.ExistingStackInfoBuilder;
import software.wings.service.intfc.aws.delegate.AwsCFHelperServiceDelegate;

import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackEventsRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackEvent;
import com.amazonaws.services.cloudformation.model.StackResource;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.cloudformation.model.UpdateStackRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@OwnedBy(CDP)
public class CloudformationBaseHelperImpl implements CloudformationBaseHelper {
  public static final String CLOUDFORMATION_STACK_CREATE_URL = "Create URL";
  public static final String CLOUDFORMATION_STACK_CREATE_BODY = "Create Body";
  public static final String CLOUDFORMATION_STACK_CREATE_GIT = "Create GIT";

  @Inject protected AWSCloudformationClient awsCloudformationClient;
  @Inject protected AwsCFHelperServiceDelegate awsCFHelperServiceDelegate;
  @Inject protected SecretDecryptionService secretDecryptionService;
  @Inject protected AwsNgConfigMapper awsNgConfigMapper;
  @Inject protected AwsCloudformationPrintHelper awsCloudformationPrintHelper;

  public List<Tag> getCloudformationTags(String tagsJson) throws IOException {
    List<Tag> tags = null;
    if (isNotEmpty(tagsJson)) {
      ObjectMapper mapper = new ObjectMapper();
      tags = Arrays.asList(mapper.readValue(tagsJson, Tag[].class));
    }
    return tags;
  }

  public Set<String> getCapabilities(AwsInternalConfig awsInternalConfig, String region, String data,
      List<String> userDefinedCapabilities, String templateType) {
    List<String> capabilities =
        awsCFHelperServiceDelegate.getCapabilities(awsInternalConfig, region, data, templateType);
    Set<String> allCapabilities = new HashSet<>();

    if (isNotEmpty(userDefinedCapabilities)) {
      allCapabilities.addAll(userDefinedCapabilities);
    }

    allCapabilities.addAll(capabilities);
    return allCapabilities;
  }

  public long printStackEvents(AwsInternalConfig awsInternalConfig, String region, long stackEventsTs, Stack stack,
      LogCallback executionLogCallback) {
    List<StackEvent> stackEvents = getStackEvents(awsInternalConfig, region, stack, stackEventsTs);
    return awsCloudformationPrintHelper.printStackEvents(stackEvents, stackEventsTs, executionLogCallback);
  }

  public void printStackResources(
      AwsInternalConfig awsInternalConfig, String region, Stack stack, LogCallback executionLogCallback) {
    if (stack == null) {
      return;
    }
    List<StackResource> stackResources = getStackResources(awsInternalConfig, region, stack);
    awsCloudformationPrintHelper.printStackResources(stackResources, executionLogCallback);
  }

  public ExistingStackInfo getExistingStackInfo(
      AwsInternalConfig awsInternalConfig, String region, Stack originalStack) {
    ExistingStackInfoBuilder builder = ExistingStackInfo.builder();
    builder.stackExisted(true);
    builder.oldStackParameters(originalStack.getParameters().stream().collect(
        toMap(Parameter::getParameterKey, Parameter::getParameterValue)));
    builder.oldStackBody(
        awsCFHelperServiceDelegate.getStackBody(awsInternalConfig, region, originalStack.getStackId()));
    return builder.build();
  }

  @Override
  public AwsInternalConfig getAwsInternalConfig(
      AwsConnectorDTO awsConnectorDTO, String region, List<EncryptedDataDetail> encryptedDataDetails) {
    if (awsConnectorDTO.getCredential() != null && awsConnectorDTO.getCredential().getConfig() != null) {
      secretDecryptionService.decrypt(
          (AwsManualConfigSpecDTO) awsConnectorDTO.getCredential().getConfig(), encryptedDataDetails);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          (AwsManualConfigSpecDTO) awsConnectorDTO.getCredential().getConfig(), encryptedDataDetails);
    }
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    awsInternalConfig.setDefaultRegion(region);
    return awsInternalConfig;
  }

  @Override
  public void deleteStack(String region, AwsInternalConfig awsConfig, String stackName, String roleARN, int timeout) {
    DeleteStackRequest deleteStackRequest = new DeleteStackRequest();
    deleteStackRequest.withStackName(stackName);
    if (isNotEmpty(roleARN)) {
      deleteStackRequest.withRoleARN(roleARN);
    }
    deleteStackRequest.withSdkRequestTimeout(timeout);
    awsCloudformationClient.deleteStack(region, deleteStackRequest, awsConfig);
  }

  @Override
  public void waitForStackToBeDeleted(
      String region, AwsInternalConfig awsInternalConfig, String stackId, LogCallback logCallback, long stackEventsTs) {
    DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest().withStackName(stackId);
    awsCloudformationClient.waitForStackDeletionCompleted(
        describeStacksRequest, awsInternalConfig, region, logCallback, stackEventsTs);
  }

  private List<StackResource> getStackResources(AwsInternalConfig awsInternalConfig, String region, Stack stack) {
    return awsCloudformationClient.getAllStackResources(
        region, new DescribeStackResourcesRequest().withStackName(stack.getStackId()), awsInternalConfig);
  }

  private List<StackEvent> getStackEvents(
      AwsInternalConfig awsInternalConfig, String region, Stack stack, long stackEventsTs) {
    return awsCloudformationClient.getAllStackEvents(
        region, new DescribeStackEventsRequest().withStackName(stack.getStackId()), awsInternalConfig, stackEventsTs);
  }

  public DeployStackRequest transformToDeployStackRequest(UpdateStackRequest updateStackRequest) {
    return DeployStackRequest.builder()
        .stackName(updateStackRequest.getStackName())
        .parameters(updateStackRequest.getParameters())
        .capabilities(updateStackRequest.getCapabilities())
        .tags(updateStackRequest.getTags())
        .roleARN(updateStackRequest.getRoleARN())
        .templateBody(updateStackRequest.getTemplateBody())
        .templateURL(updateStackRequest.getTemplateURL())
        .build();
  }
}
