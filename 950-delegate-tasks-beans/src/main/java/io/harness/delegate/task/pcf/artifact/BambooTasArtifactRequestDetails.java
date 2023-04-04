/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.expression.Expression;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
public class BambooTasArtifactRequestDetails implements TasArtifactRequestDetails {
  @Expression(ALLOW_SECRETS) String build;
  @Expression(ALLOW_SECRETS) String planKey;
  @Expression(ALLOW_SECRETS) List<String> artifactPaths;
  private String identifier;

  public String getArtifactPath() {
    return artifactPaths.stream().findFirst().orElseThrow(
        () -> new InvalidArgumentsException("Expected at least a single artifact path"));
  }

  @Override
  public String getArtifactName() {
    return getArtifactPath();
  }
}
