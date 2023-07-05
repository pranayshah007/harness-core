/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.exception.InvalidRequestException;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.hibernate.validator.constraints.NotEmpty;

public class PluginInfoProviderUtils {
  public ManifestOutcome getServerlessManifestOutcome(
      @NotEmpty Collection<ManifestOutcome> manifestOutcomes, String manifestType) {
    List<ManifestOutcome> serverlessManifests =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> manifestType.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());
    if (isEmpty(serverlessManifests)) {
      throw new InvalidRequestException("Manifests are mandatory for Serverless Aws Lambda step", USER);
    }
    if (serverlessManifests.size() > 1) {
      throw new InvalidRequestException("There can be only a single manifest for Serverless Aws Lambda step", USER);
    }
    return serverlessManifests.get(0);
  }
}
