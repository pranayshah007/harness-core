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
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class AwsLambdaCustomArtifactConfig implements AwsLambdaArtifactConfig, NestedAnnotationResolver {
  String identifier;
  boolean primaryArtifact;
  String version;
  Map<String, String> metadata;
  @NonFinal @Expression(ALLOW_SECRETS) String bucketName;
  @NonFinal @Expression(ALLOW_SECRETS) String filePath;

  @Override
  public AwsLambdaArtifactType getAwsLambdaArtifactType() {
    return AwsLambdaArtifactType.CUSTOM_ARTIFACT;
  }
}
