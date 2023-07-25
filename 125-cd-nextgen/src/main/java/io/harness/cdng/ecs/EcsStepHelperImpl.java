/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class EcsStepHelperImpl implements EcsStepHelper {
  @Override
  public List<ManifestOutcome> getEcsManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    // Filter only ecs supported manifest types
    Map<String, List<ManifestOutcome>> manifestTypeMap =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> ManifestType.ECS_SUPPORTED_MANIFEST_TYPES.contains(manifestOutcome.getType()))
            .collect(Collectors.groupingBy(ManifestOutcome::getType));

    List<ManifestOutcome> ecsManifests = new ArrayList<>();

    manifestTypeMap.forEach((type, manifests) -> {
      if (ManifestType.EcsTaskDefinition.equals(type) || ManifestType.EcsServiceDefinition.equals(type)) {
        ecsManifests.add(manifests.stream().min(Comparator.comparingInt(ManifestOutcome::getOrder).reversed()).get());
      } else {
        ecsManifests.addAll(manifests);
      }
    });

    // Check if ECS Manifests are empty
    if (isEmpty(ecsManifests)) {
      throw new InvalidRequestException("ECS Task Definition, Service Definition Manifests are mandatory.", USER);
    }

    // Get ECS Task definition manifests and validate
    List<ManifestOutcome> ecsTaskDefinitions =
        ecsManifests.stream()
            .filter(ecsManifest -> ManifestType.EcsTaskDefinition.equals(ecsManifest.getType()))
            .collect(Collectors.toList());

    if (ecsTaskDefinitions.size() > 1) {
      throw new InvalidRequestException(
          format("Only one ECS Task Definition manifest is expected. Found %s.", ecsTaskDefinitions.size()), USER);
    }

    // Get ECS Service definition manifests and validate
    List<ManifestOutcome> ecsServiceDefinitions =
        ecsManifests.stream()
            .filter(ecsManifest -> ManifestType.EcsServiceDefinition.equals(ecsManifest.getType()))
            .collect(Collectors.toList());

    if (isEmpty(ecsServiceDefinitions)) {
      throw new InvalidRequestException("ECS Service Definition manifest is mandatory, but not found.", USER);
    } else if (ecsServiceDefinitions.size() > 1) {
      throw new InvalidRequestException(
          format("Only one ECS Service Definition manifest is expected. Found %s.", ecsServiceDefinitions.size()),
          USER);
    }

    return ecsManifests;
  }

  @Override
  public ManifestOutcome getEcsTaskDefinitionManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> manifestOutcomeList =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> ManifestType.EcsTaskDefinition.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());
    return manifestOutcomeList.isEmpty() ? null : manifestOutcomeList.get(0);
  }

  @Override
  public ManifestOutcome getEcsServiceDefinitionManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    return manifestOutcomes.stream()
        .filter(manifestOutcome -> ManifestType.EcsServiceDefinition.equals(manifestOutcome.getType()))
        .collect(Collectors.toList())
        .get(0);
  }

  @Override
  public ManifestOutcome getEcsRunTaskRequestDefinitionManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    return manifestOutcomes.stream()
        .filter(manifestOutcome -> ManifestType.EcsRunTaskRequestDefinition.equals(manifestOutcome.getType()))
        .collect(Collectors.toList())
        .get(0);
  }

  @Override
  public List<ManifestOutcome> getManifestOutcomesByType(
      Collection<ManifestOutcome> manifestOutcomes, String manifestType) {
    return manifestOutcomes.stream()
        .filter(manifestOutcome -> manifestType.equals(manifestOutcome.getType()))
        .collect(Collectors.toList());
  }
}
