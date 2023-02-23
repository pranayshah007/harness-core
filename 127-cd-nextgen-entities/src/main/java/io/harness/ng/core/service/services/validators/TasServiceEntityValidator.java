/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.services.validators;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.kinds.AutoScalerManifest;
import io.harness.cdng.manifest.yaml.kinds.TasManifest;
import io.harness.cdng.service.beans.TanzuApplicationServiceSpec;
import io.harness.exception.InvalidYamlException;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.yaml.NGServiceConfig;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.CDP)
public class TasServiceEntityValidator implements ServiceEntityValidator {
  @Override
  public void validate(@NotNull @Valid ServiceEntity serviceEntity) {
    try {
      NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity);
      TanzuApplicationServiceSpec tanzuApplicationServiceSpec =
          (TanzuApplicationServiceSpec) ngServiceConfig.getNgServiceV2InfoConfig()
              .getServiceDefinition()
              .getServiceSpec();
      if (isNull(tanzuApplicationServiceSpec.getManifests())) {
        throw new InvalidYamlException("At least one manifest is required for TAS");
      }
      List<TasManifest> tasManifests = getTasManifests(tanzuApplicationServiceSpec.getManifests());
      if (tasManifests.size() != 1) {
        throw new InvalidYamlException("One Tas Manifest is required");
      }

      List<AutoScalerManifest> autoScalarManifests = getAutoScalarManifests(tanzuApplicationServiceSpec.getManifests());
      if (autoScalarManifests.size() > 1) {
        throw new InvalidYamlException("Only one AutoScalar Manifest is supported");
      }
      if (!isNull(tasManifests.get(0).getAutoScalerPath())) {
        List<String> autoScalarPaths = tasManifests.get(0).getAutoScalerPath().getValue();
        if (isNotEmpty(autoScalarManifests)
            && (!isNull(tasManifests.get(0).getAutoScalerPath().getExpressionValue()) || isNotEmpty(autoScalarPaths))) {
          throw new InvalidYamlException("Only one AutoScalar Manifest is supported");
        }
        if (isNotEmpty(autoScalarPaths) && autoScalarPaths.size() > 1) {
          throw new InvalidYamlException("Only one AutoScalar Manifest is supported");
        }
      }

    } catch (Exception e) {
      throw new InvalidYamlException(format("Invalid service yaml for Tanzu Application Service: %s", e.getMessage()));
    }
  }

  private List<TasManifest> getTasManifests(List<ManifestConfigWrapper> manifests) {
    List<TasManifest> tasManifests = new ArrayList<>();
    for (ManifestConfigWrapper manifestConfigWrapper : manifests) {
      if (manifestConfigWrapper.getManifest().getType().equals(ManifestConfigType.TAS_MANIFEST)) {
        tasManifests.add((TasManifest) manifestConfigWrapper.getManifest().getSpec());
      }
    }
    return tasManifests;
  }

  private List<AutoScalerManifest> getAutoScalarManifests(List<ManifestConfigWrapper> manifests) {
    List<AutoScalerManifest> autoScalarManifests = new ArrayList<>();
    for (ManifestConfigWrapper manifestConfigWrapper : manifests) {
      if (manifestConfigWrapper.getManifest().getType().equals(ManifestConfigType.TAS_AUTOSCALER)) {
        autoScalarManifests.add((AutoScalerManifest) manifestConfigWrapper.getManifest().getSpec());
      }
    }
    return autoScalarManifests;
  }
}
