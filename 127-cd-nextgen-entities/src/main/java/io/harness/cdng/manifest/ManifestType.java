/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Arrays;
import java.util.HashSet;

@OwnedBy(HarnessTeam.CDP)
public interface ManifestType {
  String K8Manifest = "K8sManifest";
  String VALUES = "Values";
  String CONFIG_FILE = "configFiles";
  String HelmChart = "HelmChart";
  String Kustomize = "Kustomize";
  String OpenshiftTemplate = "OpenshiftTemplate";
  String OpenshiftParam = "OpenshiftParam";
  String KustomizePatches = "KustomizePatches";
  String ServerlessAwsLambda = "ServerlessAwsLambda";
  String ReleaseRepo = "ReleaseRepo";

  static HashSet<String> getAllManifestTypes() {
    return new HashSet<>(Arrays.asList(ManifestType.K8Manifest, ManifestType.VALUES, ManifestType.OpenshiftTemplate,
        ManifestType.KustomizePatches, ManifestType.Kustomize, ManifestType.HelmChart, ManifestType.CONFIG_FILE,
        ManifestType.OpenshiftParam, ManifestType.ServerlessAwsLambda, ManifestType.ReleaseRepo));
  }
}
