/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators;

import static io.harness.cdng.manifest.ManifestType.SERVICE_OVERRIDE_SUPPORTED_MANIFEST_TYPES;
import static io.harness.cdng.service.steps.constants.ServiceOverrideConstants.overrideMapper;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideRequestDTOV2;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;

public interface ServiceOverrideValidatorService {
  void validateRequestOrThrow(@NonNull ServiceOverrideRequestDTOV2 requestDTOV2, @NonNull String accountId);

  @NonNull String generateServiceOverrideIdentifier(@NonNull NGServiceOverridesEntity serviceOverridesEntity);

  void validateServiceOverrideRequestBasicChecksOrThrow(
      @NonNull ServiceOverrideRequestDTOV2 serviceOverrideRequestDTOV2, @NonNull String accountId);

  void validateEnvironmentRBACOrThrow(@NonNull Environment environment);

  void validateEnvWithRBACOrThrow(@NonNull String accountId, String orgId, String projectId, String environmentRef);

  void checkForImmutablePropertiesOrThrow(
      NGServiceOverridesEntity existingEntity, NGServiceOverridesEntity requestedEntity);

  static void validateAllowedManifestTypesInOverrides(
      List<ManifestConfigWrapper> svcOverrideManifests, String overrideLocation) {
    if (isEmpty(svcOverrideManifests)) {
      return;
    }
    Set<String> unsupportedManifestTypesUsed =
        svcOverrideManifests.stream()
            .map(ManifestConfigWrapper::getManifest)
            .filter(Objects::nonNull)
            .map(ManifestConfig::getType)
            .map(ManifestConfigType::getDisplayName)
            .filter(type -> !SERVICE_OVERRIDE_SUPPORTED_MANIFEST_TYPES.contains(type))
            .collect(Collectors.toSet());
    if (isNotEmpty(unsupportedManifestTypesUsed)) {
      throw new InvalidRequestException(format("Unsupported Manifest Types: [%s] found for %s",
          unsupportedManifestTypesUsed.stream().map(Object::toString).collect(Collectors.joining(",")),
          isNull(overrideMapper.get(overrideLocation)) ? overrideLocation : overrideMapper.get(overrideLocation)));
    }
  }
}
