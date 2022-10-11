/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.artifactory;

import io.harness.delegate.beans.connector.ConnectorTaskParams;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryCapabilityHelper;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder
public class ArtifactoryTaskParams extends ConnectorTaskParams implements ExecutionCapabilityDemander, TaskParameters {
  ArtifactoryConnectorDTO artifactoryConnectorDTO;
  TaskType taskType;
  List<EncryptedDataDetail> encryptedDataDetails;
  String repoType;
  String repoName;
  String filePath;
  int maxVersions;

  public enum TaskType { VALIDATE, FETCH_REPOSITORIES, FETCH_BUILDS, FETCH_IMAGE_PATHS }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return ArtifactoryCapabilityHelper.fetchRequiredExecutionCapabilities(artifactoryConnectorDTO, maskingEvaluator);
  }
}