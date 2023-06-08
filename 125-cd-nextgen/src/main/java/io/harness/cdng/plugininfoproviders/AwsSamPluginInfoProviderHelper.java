/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.AwsSamDirectoryManifestOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class AwsSamPluginInfoProviderHelper {
  public ManifestOutcome getAwsSamDirectoryManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> manifestOutcomeList =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> ManifestType.AwsSamDirectory.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());
    return manifestOutcomeList.isEmpty() ? null : manifestOutcomeList.get(0);
  }

  public ManifestOutcome getAwsSamValuesManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> manifestOutcomeList =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> ManifestType.VALUES.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());
    return manifestOutcomeList.isEmpty() ? null : manifestOutcomeList.get(0);
  }

  public String getSamDirectoryPathFromAwsSamDirectoryManifestOutcome(
      AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) awsSamDirectoryManifestOutcome.getStore();

    return awsSamDirectoryManifestOutcome.getIdentifier() + "/" + gitStoreConfig.getPaths().getValue().get(0);
  }
}
