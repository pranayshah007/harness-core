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
    String samDirectoryPath =
        awsSamDirectoryManifestOutcome.getIdentifier() + "/" + gitStoreConfig.getPaths().getValue().get(0);
    return samDirectoryPath;
  }
}
