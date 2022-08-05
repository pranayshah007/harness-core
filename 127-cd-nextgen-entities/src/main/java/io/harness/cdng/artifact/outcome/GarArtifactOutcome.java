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
@TypeAlias("GarArtifactOutcome")
@JsonTypeName("GarArtifactOutcome")
@OwnedBy(CDC)
@RecasterAlias("io.harness.ngpipeline.artifact.bean.GarArtifactOutcome")
public class GarArtifactOutcome implements ArtifactOutcome {
  String connectorRef;
  String project;
  String repositoryName;
  String region;
  String pkg;
  String version;
  String versionRegex;
  String type;
  String identifier;
  boolean primaryArtifact;
  @Override
  public ArtifactSummary getArtifactSummary() {
    return null;
  }
  @Override
  public String getArtifactType() {
    return type;
  }

  @Override
  public String getTag() {
    return version;
  }
}
