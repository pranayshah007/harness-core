/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ServerlessAwsLambdaManifestOutcome;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ServerlessV2PluginInfoProviderHelper {
  // todo: merge with AwsSamPluginInfoProviderHelper
  public ManifestOutcome getServerlessAwsLambdaDirectoryManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> manifestOutcomeList =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> ManifestType.ServerlessAwsLambda.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());
    return manifestOutcomeList.isEmpty() ? null : manifestOutcomeList.get(0);
  }

  public ManifestOutcome getServerlessAwsLambdaValuesManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> manifestOutcomeList =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> ManifestType.VALUES.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());
    return manifestOutcomeList.isEmpty() ? null : manifestOutcomeList.get(0);
  }

  public String getServerlessAwsLambdaDirectoryPathFromManifestOutcome(
      ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaManifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) serverlessAwsLambdaManifestOutcome.getStore();

    String path =
        serverlessAwsLambdaManifestOutcome.getIdentifier() + "/" + gitStoreConfig.getPaths().getValue().get(0);
    path = path.replaceAll("/$", "");
    return path;
  }
}
