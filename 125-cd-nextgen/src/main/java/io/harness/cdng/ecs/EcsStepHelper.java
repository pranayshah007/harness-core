package io.harness.cdng.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.Collection;
import java.util.List;

@OwnedBy(HarnessTeam.CDP)
public interface EcsStepHelper {
  List<ManifestOutcome> getEcsManifestOutcome(@NotEmpty Collection<ManifestOutcome> manifestOutcomes);
  ManifestOutcome getEcsTaskDefinitionManifestOutcome(@NotEmpty Collection<ManifestOutcome> manifestOutcomes);
  ManifestOutcome getEcsServiceDefinitionManifestOutcome(@NotEmpty Collection<ManifestOutcome> manifestOutcomes);
  List<ManifestOutcome> getManifestOutcomesByType(@NotEmpty Collection<ManifestOutcome> manifestOutcomes, String manifestType);
}
