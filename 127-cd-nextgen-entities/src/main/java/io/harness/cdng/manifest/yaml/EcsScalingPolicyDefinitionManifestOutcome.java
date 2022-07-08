package io.harness.cdng.manifest.yaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("ecsScalingPolicyDefinitionManifestOutcome")
@JsonTypeName(ManifestType.EcsScalingPolicyDefinition)
@RecasterAlias("io.harness.cdng.manifest.yaml.EcsScalingPolicyDefinitionManifestOutcome")
public class EcsScalingPolicyDefinitionManifestOutcome implements ManifestOutcome {
    String identifier;
    String type = ManifestType.EcsScalingPolicyDefinition;
    StoreConfig store;
    int order;
}
