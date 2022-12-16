/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class AsgStepHelper {
  public Map<String, Map<String, List<ManifestOutcome>>> getStoreManifestMap(
      Collection<ManifestOutcome> manifestOutcomes) {
    // Filter only asg supported manifest types
    List<ManifestOutcome> asgManifests =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> ManifestType.ASG_SUPPORTED_MANIFEST_TYPES.contains(manifestOutcome.getType()))
            .collect(Collectors.toList());

    // Check if ASG Manifests are empty
    if (isEmpty(asgManifests)) {
      throw new InvalidRequestException("ASG Task Definition, Service Definition Manifests are mandatory.", USER);
    }

    // Get AsgLaunchTemplate manifests and validate
    List<ManifestOutcome> asgLaunchTemplates =
        filterManifestOutcomesByType(asgManifests, ManifestType.AsgLaunchTemplate);
    assertManifestOutcomeSizeOne(asgLaunchTemplates, ManifestType.AsgLaunchTemplate);

    // Get AsgConfiguration manifests and validate
    List<ManifestOutcome> asgConfigurations = filterManifestOutcomesByType(asgManifests, ManifestType.AsgConfiguration);
    assertManifestOutcomeSizeOne(asgConfigurations, ManifestType.AsgConfiguration);

    // Get AsgScalingPolicy manifests
    List<ManifestOutcome> asgScalingPolicies =
        filterManifestOutcomesByType(asgManifests, ManifestType.AsgScalingPolicy);

    // Get AsgScalingPolicy manifests
    List<ManifestOutcome> asgScheduledUpdateGroupActions =
        filterManifestOutcomesByType(asgManifests, ManifestType.AsgScheduledUpdateGroupAction);

    Map<String, Map<String, List<ManifestOutcome>>> storeManifestMap = new HashMap<>();
    ManifestOutcome asgLaunchTemplateOutcome = asgLaunchTemplates.get(0);
    storeManifestMap.put(asgLaunchTemplateOutcome.getStore().getKind(), new HashMap<>() {
      { put(ManifestType.AsgLaunchTemplate, Arrays.asList(asgLaunchTemplateOutcome)); }
    });

    populateHarnessOrGitStoreManifestOutcomeMap(storeManifestMap, asgConfigurations);
    populateHarnessOrGitStoreManifestOutcomeMap(storeManifestMap, asgScalingPolicies);
    populateHarnessOrGitStoreManifestOutcomeMap(storeManifestMap, asgScheduledUpdateGroupActions);

    return storeManifestMap;
  }

  private List<ManifestOutcome> filterManifestOutcomesByType(List<ManifestOutcome> list, String type) {
    return list.stream().filter(manifestOutcome -> type.equals(manifestOutcome.getType())).collect(Collectors.toList());
  }

  private void assertManifestOutcomeSizeOne(List<ManifestOutcome> list, String manifestType) {
    if (isEmpty(list)) {
      throw new InvalidRequestException(format("%s manifest is mandatory, but not found.", manifestType), USER);
    } else if (list.size() > 1) {
      throw new InvalidRequestException(
          format("Only one %s manifest is expected. Found %s.", manifestType, list.size()), USER);
    }
  }

  private void populateHarnessOrGitStoreManifestOutcomeMap(
      Map<String, Map<String, List<ManifestOutcome>>> storeManifestMap, List<ManifestOutcome> manifestOutcomes) {
    if (manifestOutcomes == null) {
      return;
    }

    manifestOutcomes.stream().forEach(manifestOutcome -> {
      String storeKind = manifestOutcome.getStore().getKind();
      storeManifestMap.putIfAbsent(storeKind, new HashMap<>());
      Map<String, List<ManifestOutcome>> manifestMap = storeManifestMap.get(storeKind);
      String manifestType = manifestOutcome.getType();
      manifestMap.putIfAbsent(manifestType, new ArrayList<>());
      List<ManifestOutcome> list = manifestMap.get(manifestType);
      list.add(manifestOutcome);
    });
  }
}
