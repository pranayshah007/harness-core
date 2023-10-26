/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.ManifestSourceWrapper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(CDP)
@UtilityClass
@Slf4j
public class K8sApplyStepHelper {
  public static void addOverrideConnectorRef(
      Map<String, ParameterField<String>> connectorRefMap, List<ManifestConfigWrapper> overrides) {
    if (EmptyPredicate.isNotEmpty(overrides)) {
      for (ManifestConfigWrapper manifestConfigWrapper : overrides) {
        if (manifestConfigWrapper.getManifest().getSpec().getStoreConfig().getConnectorReference() != null) {
          connectorRefMap.put("configuration.overrides." + manifestConfigWrapper.getManifest().getIdentifier()
                  + ".spec.store.spec.connectorRef",
              manifestConfigWrapper.getManifest().getSpec().getStoreConfig().getConnectorReference());
        }
      }
    }
  }

  public static void addManifestSourceConnectorRef(
      Map<String, ParameterField<String>> connectorRefMap, ManifestSourceWrapper manifestSourceWrapper) {
    if (manifestSourceWrapper != null
        && (manifestSourceWrapper.getSpec().getStoreConfig().getConnectorReference() != null)) {
      connectorRefMap.put("configuration.manifestSource.spec.store.spec.connectorRef",
          manifestSourceWrapper.getSpec().getStoreConfig().getConnectorReference());
    }
  }
}
