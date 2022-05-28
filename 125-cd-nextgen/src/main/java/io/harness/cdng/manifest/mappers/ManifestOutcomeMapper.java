/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.mappers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.ManifestType.HelmChart;
import static io.harness.cdng.manifest.ManifestType.K8Manifest;
import static io.harness.cdng.manifest.ManifestType.Kustomize;
import static io.harness.cdng.manifest.ManifestType.KustomizePatches;
import static io.harness.cdng.manifest.ManifestType.OpenshiftParam;
import static io.harness.cdng.manifest.ManifestType.OpenshiftTemplate;
import static io.harness.cdng.manifest.ManifestType.ServerlessAwsLambda;
import static io.harness.cdng.manifest.ManifestType.VALUES;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizeManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizePatchesManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftParamManifestOutcome;
import io.harness.cdng.manifest.yaml.ServerlessAwsLambdaManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.manifest.yaml.kinds.HelmChartManifest;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.manifest.yaml.kinds.KustomizeManifest;
import io.harness.cdng.manifest.yaml.kinds.KustomizePatchesManifest;
import io.harness.cdng.manifest.yaml.kinds.OpenshiftManifest;
import io.harness.cdng.manifest.yaml.kinds.OpenshiftParamManifest;
import io.harness.cdng.manifest.yaml.kinds.ServerlessAwsLambdaManifest;
import io.harness.cdng.manifest.yaml.kinds.ValuesManifest;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDP)
public class ManifestOutcomeMapper {
  public List<ManifestOutcome> toManifestOutcome(List<ManifestAttributes> manifestAttributesList, int order) {
    return manifestAttributesList.stream()
        .map(manifest -> toManifestOutcome(manifest, order))
        .collect(Collectors.toCollection(LinkedList::new));
  }

  public ManifestOutcome toManifestOutcome(ManifestAttributes manifestAttributes, int order) {
    if (manifestAttributes.getStoreConfig() != null) {
      ManifestOutcomeValidator.validateStore(
          manifestAttributes.getStoreConfig(), manifestAttributes.getKind(), manifestAttributes.getIdentifier(), true);
    }

    switch (manifestAttributes.getKind()) {
      case K8Manifest:
        return getK8sOutcome(manifestAttributes);
      case VALUES:
        return getValuesOutcome(manifestAttributes, order);
      case HelmChart:
        return getHelmChartOutcome(manifestAttributes);
      case Kustomize:
        return getKustomizeOutcome(manifestAttributes);
      case KustomizePatches:
        return getKustomizePatchesOutcome(manifestAttributes, order);
      case OpenshiftTemplate:
        return getOpenshiftOutcome(manifestAttributes);
      case OpenshiftParam:
        return getOpenshiftParamOutcome(manifestAttributes, order);
      case ServerlessAwsLambda:
        return getServerlessAwsOutcome(manifestAttributes, order);
      default:
        throw new UnsupportedOperationException(
            format("Unknown Artifact Config type: [%s]", manifestAttributes.getKind()));
    }
  }

  private K8sManifestOutcome getK8sOutcome(ManifestAttributes manifestAttributes) {
    K8sManifest k8sManifest = (K8sManifest) manifestAttributes;

    return K8sManifestOutcome.builder()
        .identifier(k8sManifest.getIdentifier())
        .store(k8sManifest.getStoreConfig())
        .valuesPaths(k8sManifest.getValuesPaths())
        .skipResourceVersioning(k8sManifest.getSkipResourceVersioning())
        .build();
  }

  private ValuesManifestOutcome getValuesOutcome(ManifestAttributes manifestAttributes, int order) {
    ValuesManifest attributes = (ValuesManifest) manifestAttributes;
    return ValuesManifestOutcome.builder()
        .identifier(attributes.getIdentifier())
        .store(attributes.getStoreConfig())
        .order(order)
        .build();
  }

  private HelmChartManifestOutcome getHelmChartOutcome(ManifestAttributes manifestAttributes) {
    HelmChartManifest helmChartManifest = (HelmChartManifest) manifestAttributes;

    return HelmChartManifestOutcome.builder()
        .identifier(helmChartManifest.getIdentifier())
        .store(helmChartManifest.getStoreConfig())
        .chartName(helmChartManifest.getChartName())
        .chartVersion(helmChartManifest.getChartVersion())
        .helmVersion(helmChartManifest.getHelmVersion())
        .valuesPaths(helmChartManifest.getValuesPaths())
        .skipResourceVersioning(helmChartManifest.getSkipResourceVersioning())
        .commandFlags(helmChartManifest.getCommandFlags())
        .build();
  }

  private KustomizeManifestOutcome getKustomizeOutcome(ManifestAttributes manifestAttributes) {
    KustomizeManifest kustomizeManifest = (KustomizeManifest) manifestAttributes;
    return KustomizeManifestOutcome.builder()
        .identifier(kustomizeManifest.getIdentifier())
        .store(kustomizeManifest.getStoreConfig())
        .skipResourceVersioning(kustomizeManifest.getSkipResourceVersioning())
        .pluginPath(kustomizeManifest.getPluginPath())
        .patchesPaths(kustomizeManifest.getPatchesPaths())
        .build();
  }

  private KustomizePatchesManifestOutcome getKustomizePatchesOutcome(ManifestAttributes manifestAttributes, int order) {
    KustomizePatchesManifest attributes = (KustomizePatchesManifest) manifestAttributes;
    return KustomizePatchesManifestOutcome.builder()
        .identifier(attributes.getIdentifier())
        .store(attributes.getStoreConfig())
        .order(order)
        .build();
  }

  private OpenshiftManifestOutcome getOpenshiftOutcome(ManifestAttributes manifestAttributes) {
    OpenshiftManifest openshiftManifest = (OpenshiftManifest) manifestAttributes;

    return OpenshiftManifestOutcome.builder()
        .identifier(openshiftManifest.getIdentifier())
        .store(openshiftManifest.getStoreConfig())
        .skipResourceVersioning(openshiftManifest.getSkipResourceVersioning())
        .paramsPaths(openshiftManifest.getParamsPaths())
        .build();
  }

  private OpenshiftParamManifestOutcome getOpenshiftParamOutcome(ManifestAttributes manifestAttributes, int order) {
    OpenshiftParamManifest attributes = (OpenshiftParamManifest) manifestAttributes;

    return OpenshiftParamManifestOutcome.builder()
        .identifier(attributes.getIdentifier())
        .store(attributes.getStoreConfig())
        .order(order)
        .build();
  }

  private ServerlessAwsLambdaManifestOutcome getServerlessAwsOutcome(ManifestAttributes manifestAttributes, int order) {
    ServerlessAwsLambdaManifest attributes = (ServerlessAwsLambdaManifest) manifestAttributes;
    return ServerlessAwsLambdaManifestOutcome.builder()
        .identifier(attributes.getIdentifier())
        .store(attributes.getStoreConfig())
        .configOverridePath(attributes.getConfigOverridePath())
        .order(order)
        .build();
  }
}
