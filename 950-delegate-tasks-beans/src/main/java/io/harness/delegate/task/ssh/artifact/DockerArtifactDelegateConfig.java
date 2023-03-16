/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ssh.artifact;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class DockerArtifactDelegateConfig implements SkipCopyArtifactDelegateConfig, NestedAnnotationResolver {
  String identifier;
  boolean primaryArtifact;
  String imagePath;
  String tag;

  @Override
  public String getIdentifier() {
    return identifier;
  }

  @Override
  public SshWinRmArtifactType getArtifactType() {
    return SshWinRmArtifactType.DOCKER;
  }

  @Override
  public String getArtifactPath() {
    return format("%s:%s", imagePath, tag);
  }
}
