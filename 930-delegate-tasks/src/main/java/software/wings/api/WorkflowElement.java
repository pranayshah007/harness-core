/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.ArtifactVariable;
import software.wings.beans.ManifestVariable;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowElement {
  private String uuid;
  private String name;
  private String displayName;
  private String description;
  private String releaseNo;
  private String url;
  private Map<String, Object> variables;
  private String lastGoodDeploymentUuid;
  private String lastGoodDeploymentDisplayName;
  private String lastGoodReleaseNo;
  private String pipelineDeploymentUuid;
  private String pipelineResumeUuid;
  private Long startTs;
  private List<ArtifactVariable> artifactVariables;
  private List<ManifestVariable> manifestVariables;
}
