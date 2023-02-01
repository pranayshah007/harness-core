/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.validator;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.cdng.configfile.ConfigFile;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.kinds.HelmChartManifest;
import io.harness.cdng.manifest.yaml.kinds.HelmRepoOverrideManifest;
import io.harness.exception.InvalidRequestException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

@UtilityClass
public class EnvironmentV2ManifestValidator {
  public static void checkDuplicateManifestIdentifiersWithIn(List<ManifestConfigWrapper> manifests) {
    if (isEmpty(manifests)) {
      return;
    }
    final Stream<String> identifierStream =
        manifests.stream().map(ManifestConfigWrapper::getManifest).map(ManifestConfig::getIdentifier);
    Set<String> duplicateIds = getDuplicateIdentifiers(identifierStream);
    if (isNotEmpty(duplicateIds)) {
      throw new InvalidRequestException(format("Found duplicate manifest identifiers [%s]",
          duplicateIds.stream().map(Object::toString).collect(Collectors.joining(","))));
    }
  }

  public static void validateHelmRepoOverrideContainsSameManifestType(
      HelmChartManifest serviceManifest, HelmRepoOverrideManifest overrideManifest) {
    if (serviceManifest.getStore() != null) {
      String overrideManifestKind = overrideManifest.getType();
      String serviceManifestKind = serviceManifest.getStoreConfig().getKind();

      if (isNotEmpty(overrideManifestKind) && isNotEmpty(serviceManifestKind)
          && !overrideManifestKind.equals(serviceManifestKind)) {
        throw new InvalidRequestException(format(
            "The kind of helm repo override manifest provided [%s] is different than the kind of service manifest [%s].",
            overrideManifestKind, serviceManifestKind));
      }
    }
  }

  public static void validateNoMoreThanOneHelmOverridePresent(
      List<ManifestConfigWrapper> manifests, String exceptionMessage) {
    if (isEmpty(manifests)) {
      return;
    }
    List<ManifestConfigWrapper> helmRepoOverrides =
        manifests.stream()
            .filter(manifest -> ManifestConfigType.HELM_REPO_OVERRIDE == manifest.getManifest().getType())
            .collect(Collectors.toList());
    if (helmRepoOverrides.size() > 1) {
      String helmOverrideIds = helmRepoOverrides.stream()
                                   .map(helmOverride -> helmOverride.getManifest().getIdentifier())
                                   .collect(Collectors.joining(","));
      throw new InvalidRequestException(format(exceptionMessage, helmOverrideIds));
    }
  }

  public static void checkDuplicateConfigFilesIdentifiersWithIn(List<ConfigFileWrapper> configFiles) {
    if (isEmpty(configFiles)) {
      return;
    }
    final Stream<String> identifierStream =
        configFiles.stream().map(ConfigFileWrapper::getConfigFile).map(ConfigFile::getIdentifier);
    Set<String> duplicateIds = getDuplicateIdentifiers(identifierStream);
    if (isNotEmpty(duplicateIds)) {
      throw new InvalidRequestException(format("Found duplicate configFiles identifiers [%s]",
          duplicateIds.stream().map(Object::toString).collect(Collectors.joining(","))));
    }
  }

  @NotNull
  private static Set<String> getDuplicateIdentifiers(Stream<String> identifierStream) {
    Set<String> uniqueIds = new HashSet<>();
    Set<String> duplicateIds = new HashSet<>();
    identifierStream.forEach(id -> {
      if (!uniqueIds.add(id)) {
        duplicateIds.add(id);
      }
    });
    return duplicateIds;
  }
}
