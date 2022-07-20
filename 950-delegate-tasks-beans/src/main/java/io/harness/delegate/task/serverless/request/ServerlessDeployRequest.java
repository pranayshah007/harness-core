/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless.request;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.serverless.ServerlessArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessCommandType;
import io.harness.delegate.task.serverless.ServerlessDeployConfig;
import io.harness.delegate.task.serverless.ServerlessInfraConfig;
import io.harness.delegate.task.serverless.ServerlessManifestConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionReflectionUtils.NestedAnnotationResolver;

import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class ServerlessDeployRequest implements ServerlessCommandRequest, NestedAnnotationResolver {
  String accountId;
  ServerlessCommandType serverlessCommandType;
  String commandName;
  @NonFinal @Expression(ALLOW_SECRETS) ServerlessArtifactConfig serverlessArtifactConfig;
  @NonFinal @Expression(ALLOW_SECRETS) Map<String, ServerlessArtifactConfig> sidecarServerlessArtifactConfigs;
  CommandUnitsProgress commandUnitsProgress;
  @NonFinal @Expression(ALLOW_SECRETS) ServerlessManifestConfig serverlessManifestConfig;
  @NonFinal @Expression(ALLOW_SECRETS) ServerlessInfraConfig serverlessInfraConfig;
  @NonFinal @Expression(ALLOW_SECRETS) ServerlessDeployConfig serverlessDeployConfig;
  Integer timeoutIntervalInMin;
  @Expression(ALLOW_SECRETS) String manifestContent;
}
