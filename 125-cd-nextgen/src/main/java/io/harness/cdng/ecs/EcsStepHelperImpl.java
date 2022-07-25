package io.harness.cdng.ecs;

import com.google.inject.Singleton;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.exception.InvalidRequestException;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class EcsStepHelperImpl implements EcsStepHelper {
  @Override
  public List<ManifestOutcome> getEcsManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> ecsManifests =
            manifestOutcomes.stream()
                    .filter(manifestOutcome -> ManifestType.ECS_SUPPORTED_MANIFEST_TYPES.contains(manifestOutcome.getType()))
                    .collect(Collectors.toList());
    if (isEmpty(ecsManifests)) {
      throw new InvalidRequestException("Manifests are mandatory for Ecs step", USER);
    }

    List<ManifestOutcome> ecsTaskDefinitions = ecsManifests.stream().filter(ecsManifest -> ManifestType.EcsTaskDefinition.equals(ecsManifest.getType())).collect(Collectors.toList());

    if (isEmpty(ecsTaskDefinitions)) {
      throw new InvalidRequestException("Ecs Task Definition manifest is mandatory for Ecs Step", USER);
    } else if (ecsTaskDefinitions.size() > 1) {
      throw new InvalidRequestException("Only one Ecs Task Definition is expected. Found more.", USER);
    }

    List<ManifestOutcome> ecsServiceDefinitions = ecsManifests.stream().filter(ecsManifest -> ManifestType.EcsServiceDefinition.equals(ecsManifest.getType())).collect(Collectors.toList());

    if (isEmpty(ecsServiceDefinitions)) {
      throw new InvalidRequestException("Ecs Service Definition manifest is mandatory for Ecs Step", USER);
    } else if (ecsServiceDefinitions.size() > 1) {
      throw new InvalidRequestException("Only one Ecs Service Definition is expected. Found more.", USER);
    }

    return ecsManifests;
  }

  @Override
  public ManifestOutcome getEcsTaskDefinitionManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    return manifestOutcomes.stream().filter(manifestOutcome -> ManifestType.EcsTaskDefinition.equals(manifestOutcome.getType())).collect(Collectors.toList()).get(0);
  }

  @Override
  public ManifestOutcome getEcsServiceDefinitionManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    return manifestOutcomes.stream().filter(manifestOutcome -> ManifestType.EcsServiceDefinition.equals(manifestOutcome.getType())).collect(Collectors.toList()).get(0);
  }

  @Override
  public List<ManifestOutcome> getManifestOutcomesByType(Collection<ManifestOutcome> manifestOutcomes, String manifestType) {
    return manifestOutcomes.stream().filter(manifestOutcome -> manifestType.equals(manifestOutcome.getType())).collect(Collectors.toList());
  }
}
