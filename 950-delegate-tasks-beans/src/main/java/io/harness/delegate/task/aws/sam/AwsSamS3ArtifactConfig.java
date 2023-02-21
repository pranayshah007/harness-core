/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.sam;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.security.encryption.EncryptedDataDetail;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.List;

import static io.harness.expression.Expression.ALLOW_SECRETS;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
public class AwsSamS3ArtifactConfig implements AwsSamArtifactConfig, NestedAnnotationResolver {
  @NonFinal @Expression(ALLOW_SECRETS) String bucketName;
  @NonFinal @Expression(ALLOW_SECRETS) String filePath;
  @NonFinal @Expression(ALLOW_SECRETS) String region;
  String type;
  String identifier;
  ConnectorInfoDTO connectorDTO;
  List<EncryptedDataDetail> encryptedDataDetails;

  @Override
  public AwsSamArtifactType getAwsSamArtifactType() {
    return AwsSamArtifactType.AMAZON_S3;
  }
}
