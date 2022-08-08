/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.artifact.Artifact;
import software.wings.service.intfc.ArtifactService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(CDC)
public class PhaseElementParamMapper implements ContextElementParamMapper {
  private final ArtifactService artifactService;
  private final FeatureFlagService featureFlagService;

  private final PhaseElement element;

  public PhaseElementParamMapper(
      ArtifactService artifactService, FeatureFlagService featureFlagService, PhaseElement element) {
    this.artifactService = artifactService;
    this.featureFlagService = featureFlagService;
    this.element = element;
  }

  @Override
  public Map<String, Object> paramMap(ExecutionContext context) {
    Map<String, Object> map = new HashMap<>();
    map.put(ContextElement.SERVICE, this.element.getServiceElement());

    if (this.element.getRollbackArtifactId() != null) {
      Artifact artifact = this.artifactService.getWithSource(this.element.getRollbackArtifactId());
      map.put(ContextElement.ARTIFACT, artifact);
    } else if (this.element.isRollback()
        && this.featureFlagService.isEnabled(FeatureName.ROLLBACK_NONE_ARTIFACT, context.getAccountId())) {
      // In case of rollback if don't find rollbackArtifactId, set artifact object to null.
      map.put(ContextElement.ARTIFACT, null);
    }
    return map;
  }
}