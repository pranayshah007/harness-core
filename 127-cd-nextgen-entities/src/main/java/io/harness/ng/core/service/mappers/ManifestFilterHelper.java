/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mappers;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifestConfigs.ManifestConfigurations;
import io.harness.cdng.service.ServiceSpec;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.cdng.service.beans.NativeHelmServiceSpec;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.exception.UnsupportedOperationException;
import io.harness.exception.YamlException;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class ManifestFilterHelper {
  public YamlField getManifestsNodeFromServiceYaml(YamlField serviceYamlField, String serviceIdentifier) {
    YamlField serviceDefinitionField = serviceYamlField.getNode().getField(YamlTypes.SERVICE_DEFINITION);
    if (serviceDefinitionField == null) {
      throw new YamlException(
          String.format("Yaml provided for service %s does not have service definition field.", serviceIdentifier));
    }

    String serviceDefinitionType = serviceDefinitionField.getType();
    if (!(ServiceSpecType.NATIVE_HELM.equals(serviceDefinitionType)
            || ServiceSpecType.KUBERNETES.equals(serviceDefinitionType))) {
      throw new UnsupportedOperationException(
          String.format("Service Spec Type %s is not supported", serviceDefinitionType));
    }
    return getManifestsNodeFromServiceDefinitionYaml(serviceDefinitionField);
  }

  private YamlField getManifestsNodeFromServiceDefinitionYaml(YamlField serviceDefinitionField) {
    YamlField serviceSpecField = serviceDefinitionField.getNode().getField(YamlTypes.SERVICE_SPEC);
    if (serviceSpecField == null) {
      return null;
    }

    return serviceSpecField.getNode().getField(YamlTypes.MANIFEST_LIST_CONFIG);
  }

  public List<String> getManifestIdentifiersFilteredOnManifestType(YamlField manifestsField) {
    List<String> manifestIdentifierList = new ArrayList<>();
    List<YamlNode> manifests = manifestsField.getNode().asArray();
    for (YamlNode manifestConfig : manifests) {
      if (manifestConfig == null) {
        continue;
      }
      YamlNode manifest = manifestConfig.getField(YamlTypes.MANIFEST_CONFIG).getNode();
      if (manifest != null && ManifestType.HelmChart.equals(manifest.getType())) {
        String manifestIdentifier = manifest.getIdentifier();
        manifestIdentifierList.add(manifestIdentifier);
      }
    }
    return manifestIdentifierList;
  }

  public ManifestConfigurations getManifestConfigurationsFromKubernetesAndNativeHelm(ServiceSpec spec) {
    if (spec instanceof KubernetesServiceSpec) {
      return ((KubernetesServiceSpec) spec).getManifestConfigurations();
    }

    if (spec instanceof NativeHelmServiceSpec) {
      return ((NativeHelmServiceSpec) spec).getManifestConfigurations();
    }
    return null;
  }

  public boolean hasPrimaryManifestRef(ManifestConfigurations manifestConfigurations) {
    if (manifestConfigurations == null) {
      return false;
    }
    return ParameterField.isNotNull(manifestConfigurations.getPrimaryManifestRef());
  }
}
