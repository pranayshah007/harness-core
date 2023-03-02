/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ssh.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class AcrArtifactDelegateConfig
    implements SshWinRmArtifactDelegateConfig, NestedAnnotationResolver, SkipCopyArtifactDelegateConfig {
  String identifier;
  boolean primaryArtifact;
  String subscription;
  String registry;
  String image;
  String tag;
  String tagRegex;

  @Override
  public String getIdentifier() {
    return identifier;
  }

  @Override
  public SshWinRmArtifactType getArtifactType() {
    return SshWinRmArtifactType.ACR;
  }

  @Override
  public String getArtifactPath() {
    return String.format("%s/%s:%s", registry, image, tag);
  }
}
