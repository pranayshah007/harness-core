package io.harness.cdng.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifacts.ArtifactSourceConstants;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CDC)
@Data
@Builder
@JsonTypeName(ArtifactSourceConstants.AZURE_MACHINE_IMAGE_NAME)
@RecasterAlias("io.harness.ngpipeline.pipeline.executions.beans.AzureMachineImageArtifactsSummary")
public class AzureMachineImageArtifactsSummary implements ArtifactSummary {
  /**
   * Version
   */
  String version;

  @Override
  public String getDisplayName() {
    return version;
  }

  @Override
  public String getType() {
    return ArtifactSourceConstants.AZURE_MACHINE_IMAGE_NAME;
  }
}
