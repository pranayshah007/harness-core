package io.harness.cdng.manifest.yaml;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("ecsTaskDefinitionManifestOutcome")
@JsonTypeName(ManifestType.EcsTaskDefinition)
@RecasterAlias("io.harness.cdng.manifest.yaml.EcsTaskDefinitionManifestOutcome")
public class EcsTaskDefinitionManifestOutcome implements ManifestOutcome {
  String identifier;
  String type = ManifestType.EcsTaskDefinition;
  StoreConfig store;
  int order;
}
