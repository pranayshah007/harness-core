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
@TypeAlias("ecsScalableTargetDefinitionManifestOutcome")
@JsonTypeName(ManifestType.EcsScalableTargetDefinition)
@RecasterAlias("io.harness.cdng.manifest.yaml.EcsScalableTargetDefinitionManifestOutcome")
public class EcsScalableTargetDefinitionManifestOutcome implements ManifestOutcome {
  String identifier;
  String type = ManifestType.EcsScalableTargetDefinition;
  StoreConfig store;
  int order;
}
