/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cloudformation;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.cf.DeployStackRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.helpers.ext.cloudformation.response.ExistingStackInfo;

import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.cloudformation.model.UpdateStackRequest;
import java.io.IOException;
import java.util.List;
import java.util.Set;

@OwnedBy(CDP)
public interface CloudformationBaseHelper {
  List<Tag> getCloudformationTags(String tagsJson) throws IOException;

  Set<String> getCapabilities(AwsInternalConfig awsInternalConfig, String region, String data,
      List<String> userDefinedCapabilities, String templateType);

  long printStackEvents(
      AwsInternalConfig awsInternalConfig, String region, long stackEventsTs, Stack stack, LogCallback logCallback);

  void printStackResources(
      AwsInternalConfig awsInternalConfig, String region, Stack stack, LogCallback executionLogCallback);

  ExistingStackInfo getExistingStackInfo(AwsInternalConfig awsInternalConfig, String region, Stack originalStack);

  AwsInternalConfig getAwsInternalConfig(
      AwsConnectorDTO awsConnectorDTO, String region, List<EncryptedDataDetail> encryptedDataDetails);

  void deleteStack(String region, AwsInternalConfig awsConfig, String stackName, String roleARN, int timeout);

  void waitForStackToBeDeleted(
      String region, AwsInternalConfig awsInternalConfig, String stackId, LogCallback logCallback, long stackEventsTs);

  DeployStackRequest transformToDeployStackRequest(UpdateStackRequest updateStackRequest);
}
