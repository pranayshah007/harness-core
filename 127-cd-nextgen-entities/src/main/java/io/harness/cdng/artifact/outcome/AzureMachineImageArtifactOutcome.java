package io.harness.cdng.artifact.outcome;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.ArtifactSummary;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@TypeAlias("AzureMachineImageArtifactOutcome")
@JsonTypeName("AzureMachineImageArtifactOutcome")
@OwnedBy(CDC)
@RecasterAlias("io.harness.ngpipeline.artifact.bean.AMIArtifactOutcome")
public class AzureMachineImageArtifactOutcome implements ArtifactOutcome {
  /**
   * Azure Artifacts connector.
   */
  String connectorRef;
  String imagePullSecret;

  String imageType;

  String subscriptionId;

  String resourceGroup;

  String imageGallery;

  String imageDefinition;

  /**
   * version of the package.
   */
  String version;

  /**
   * version regex is used to get latest artifacts from builds matching the regex.
   */
  String versionRegex;

  @Override
  public ArtifactSummary getArtifactSummary() {
    return null;
  }

  @Override
  public boolean isPrimaryArtifact() {
    return false;
  }

  @Override
  public String getArtifactType() {
    return null;
  }

  @Override
  public String getIdentifier() {
    return null;
  }

  @Override
  public String getTag() {
    return null;
  }
}
