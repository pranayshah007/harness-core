/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.custom;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.ScriptType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
public class CustomArtifactDelegateRequest implements ArtifactSourceDelegateRequest {
  String versionRegex;
  String version;
  /** List of buildNumbers/artifactPaths */
  String artifactsArrayPath;
  List<EncryptedDataDetail> encryptedDataDetails;
  /** Artifact Source type.*/
  ArtifactSourceType sourceType;
  Map<String, Object> attributes;
  Map<String, Object> inputs;
  String script;
  String type;
  ScriptType scriptType;
  String versionPath;
  private long timeout;
  List<String> delegateSelectors;

  public Set<String> getDelegateSelectors() {
    Set<String> combinedDelegateSelectors = new HashSet<>();
    if (delegateSelectors != null) {
      combinedDelegateSelectors.addAll(delegateSelectors);
    }
    return combinedDelegateSelectors;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return new ArrayList<>();
  }
}
