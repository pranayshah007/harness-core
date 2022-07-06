/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.s3;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * DTO object to be passed to delegate tasks.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@OwnedBy(HarnessTeam.CDC)
public class S3ArtifactDelegateRequest implements ArtifactSourceDelegateRequest {
  /** Buckets in repos need to be referenced via a path. */
  String bucketName;
  /** Tag refers to exact tag number. */
  String filePath;
  /** Tag regex is used to get latest build from builds matching regex. */
  String filePathRegex;
  /** Connector Reference*/
  String connectorRef;
  /** Aws Connector*/
  AwsConnectorDTO awsConnectorDTO;
  /** Encrypted details for decrypting.*/
  List<EncryptedDataDetail> encryptedDataDetails;
  /** Artifact Source type.*/
  ArtifactSourceType sourceType;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return null;
  }

  @Override
  public ArtifactSourceType getSourceType() {
    return null;
  }
}
