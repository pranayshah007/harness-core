package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("deploymentRepoManifestOutcome")
@JsonTypeName(ManifestType.DeploymentRepo)
@OwnedBy(GITOPS)
@RecasterAlias("io.harness.cdng.manifest.yaml.DeploymentRepoManifestOutcome")
public class DeploymentRepoManifestOutcome implements ManifestOutcome {
  String identifier;
  String type = ManifestType.DeploymentRepo;
  StoreConfig store;
}
