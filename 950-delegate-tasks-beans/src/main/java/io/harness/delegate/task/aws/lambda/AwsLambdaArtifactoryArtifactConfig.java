/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.common.collect.ImmutableMap;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class AwsLambdaArtifactoryArtifactConfig implements AwsLambdaArtifactConfig, NestedAnnotationResolver {
  @NonFinal @Expression(ALLOW_SECRETS) String repository;
  @NonFinal @Expression(ALLOW_SECRETS) List<String> artifactPaths;
  String repositoryFormat; // its value is "generic"
  String identifier;
  ConnectorConfigDTO connectorConfig;
  List<EncryptedDataDetail> encryptedDataDetails;

  public Map<String, String> toMetadata() {
    String artifactPath = Paths.get(repository, getArtifactPath()).toString();

    return ImmutableMap.of(
        ArtifactMetadataKeys.artifactName, artifactPath, ArtifactMetadataKeys.artifactPath, artifactPath);
  }

  public String getArtifactPath() {
    return artifactPaths.stream().findFirst().orElseThrow(
        () -> new InvalidArgumentsException("Expected at least a single artifact path"));
  }

  @Override
  public AwsLambdaArtifactType getAwsLambdaArtifactType() {
    return AwsLambdaArtifactType.ARTIFACTORY;
  }
}
