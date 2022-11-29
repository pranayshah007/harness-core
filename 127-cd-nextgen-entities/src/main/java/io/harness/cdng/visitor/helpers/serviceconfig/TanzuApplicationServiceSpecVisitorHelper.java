/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.visitor.helpers.serviceconfig;

import static java.util.Objects.isNull;

import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.beans.TanzuApplicationServiceSpec;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidYamlException;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractor;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class TanzuApplicationServiceSpecVisitorHelper implements ConfigValidator, EntityReferenceExtractor {
  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // Nothing to validate.
  }

  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return TanzuApplicationServiceSpec.builder().build();
  }

  @Override
  public Set<EntityDetailProtoDTO> addReference(Object object, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, Object> contextMap) {
    if (object instanceof TanzuApplicationServiceSpec) {
      TanzuApplicationServiceSpec tanzuApplicationServiceSpec = (TanzuApplicationServiceSpec) object;
      if (isNull(tanzuApplicationServiceSpec.getManifests())) {
        throw new InvalidYamlException("Atleast one manifest is required for TAS");
      }
      boolean tasManifestFound = false;
      boolean autoScalerManifestFound = false;
      for (ManifestConfigWrapper manifestConfigWrapper : tanzuApplicationServiceSpec.getManifests()) {
        if (manifestConfigWrapper.getManifest().getType().equals(ManifestConfigType.TAS_MANIFEST)) {
          if (tasManifestFound) {
            throw new InvalidYamlException("Only one TAS Manifest is supported");
          }
          tasManifestFound = true;
        } else if (manifestConfigWrapper.getManifest().getType().equals(ManifestConfigType.AUTOSCALER)) {
          if (autoScalerManifestFound) {
            throw new InvalidYamlException("Only one AutoScaler Manifest is supported");
          }
          autoScalerManifestFound = true;
        }
      }
    }
    return Collections.emptySet();
  }
}
