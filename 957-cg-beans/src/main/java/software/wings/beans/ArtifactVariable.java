/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.artifact.ArtifactInput;
import software.wings.beans.artifact.ArtifactStreamSummary;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@TargetModule(HarnessModule._957_CG_BEANS)
@OwnedBy(CDC)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ArtifactVariable extends Variable {
  private EntityType entityType;
  private String entityId;
  private List<ArtifactVariable> overriddenArtifactVariables;
  private List<ArtifactStreamSummary> artifactStreamSummaries;
  private Map<String, List<String>> displayInfo;
  private List<String> workflowIds;
  private String uiDisplayName;
  private ArtifactStreamMetadata artifactStreamMetadata;
  private LastDeployedArtifactInformation lastDeployedArtifactInfo;

  /*
  This takes artifact buildNo and artifactStreamId. When this is populated, we will ignore ArtifactVariable#value and
  will pick it from artifactInput#buildno.
   */
  private ArtifactInput artifactInput;

  @Builder
  public ArtifactVariable(String name, String description, boolean mandatory, String value, boolean fixed,
      String allowedValues, List<String> allowedList, Map<String, Object> metadata, VariableType type,
      EntityType entityType, String entityId, List<ArtifactVariable> overriddenArtifactVariables,
      List<ArtifactStreamSummary> artifactStreamSummaries, Map<String, List<String>> displayInfo,
      List<String> workflowIds, String uiDisplayName, ArtifactStreamMetadata artifactStreamMetadata,
      LastDeployedArtifactInformation lastDeployedArtifactInfo, ArtifactInput artifactInput) {
    super(name, description, mandatory, value, fixed, allowedValues, allowedList, metadata, type);
    this.entityType = entityType;
    this.entityId = entityId;
    this.overriddenArtifactVariables = overriddenArtifactVariables;
    this.artifactStreamSummaries = artifactStreamSummaries;
    this.displayInfo = displayInfo;
    this.workflowIds = workflowIds;
    this.uiDisplayName = uiDisplayName;
    this.artifactStreamMetadata = artifactStreamMetadata;
    this.lastDeployedArtifactInfo = lastDeployedArtifactInfo;
    this.artifactInput = artifactInput;
  }

  public String fetchAssociatedService() {
    switch (this.getEntityType()) {
      case SERVICE:
        return this.getEntityId();
      case ENVIRONMENT:
      case WORKFLOW:
        if (isEmpty(this.getOverriddenArtifactVariables())) {
          return null;
        }

        for (ArtifactVariable overriddenArtifactVariable : this.getOverriddenArtifactVariables()) {
          String serviceId = overriddenArtifactVariable.fetchAssociatedService();
          if (serviceId != null) {
            return serviceId;
          }
        }

        return null;
      default:
        return null;
    }
  }
}
