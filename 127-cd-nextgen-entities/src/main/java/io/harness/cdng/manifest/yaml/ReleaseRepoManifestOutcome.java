package io.harness.cdng.manifest.yaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@Value
@Builder
@TypeAlias("releaseRepoManifestOutcome")
@JsonTypeName(ManifestType.ReleaseRepo)
@OwnedBy(CDP)
@RecasterAlias("io.harness.cdng.manifest.yaml.ReleaseRepoManifestOutcome")
public class ReleaseRepoManifestOutcome implements ManifestOutcome {
    String identifier;
    String type = ManifestType.ReleaseRepo;
    StoreConfig store;
}
