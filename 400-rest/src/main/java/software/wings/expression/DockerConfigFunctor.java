/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.functors.ExpressionFunctor;

import software.wings.service.impl.artifact.ArtifactCollectionUtils;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Builder
@Slf4j
public class DockerConfigFunctor implements ExpressionFunctor {
  private String appId;
  private ArtifactCollectionUtils artifactCollectionUtils;

  private String artifactStreamId;

  @Override
  public String toString() {
    return getDockerConfig();
  }

  private String getDockerConfig() {
    log.info("Getting dockerConfig");

    try {
      return artifactCollectionUtils.getDockerConfig(artifactStreamId);
    } catch (InvalidRequestException e) {
      log.error("Error in getDockerConfig", e);
      return "";
    } finally {
      log.info("Done getting dockerConfig");
    }
  }
}
