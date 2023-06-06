/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.custom;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.task.artifacts.ArtifactSourceType.CUSTOM_ARTIFACT;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.SecretDetail;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@OwnedBy(HarnessTeam.CDC)
public class CustomArtifactDelegateRequest
    implements ArtifactSourceDelegateRequest, TaskParameters, NestedAnnotationResolver {
  String versionRegex;
  String version;
  /** List of buildNumbers/artifactPaths */
  String artifactsArrayPath;
  List<EncryptedDataDetail> encryptedDataDetails;
  /** Artifact Source type.*/
  ArtifactSourceType sourceType;
  Map<String, String> attributes;
  @Expression(ALLOW_SECRETS) Map<String, String> inputs;
  @Expression(ALLOW_SECRETS) String script;
  String type;
  String versionPath;
  String executionId;
  String workingDirectory;
  private long timeout;
  String accountId;
  List<String> delegateSelectors;
  Map<String, EncryptionConfig> encryptionConfigs;
  Map<String, SecretDetail> secretDetails;
  int expressionFunctorToken;

  public Set<String> getDelegateSelectors() {
    return isEmpty(delegateSelectors) ? Collections.EMPTY_SET : delegateSelectors.stream().collect(Collectors.toSet());
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return new ArrayList<>();
  }

  @Override
  public ArtifactSourceType getSourceType() {
    return CUSTOM_ARTIFACT;
  }
}
