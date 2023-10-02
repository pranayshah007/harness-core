/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml.summary;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import java.util.List;
import lombok.Builder;
import lombok.Value;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
@Value
@Builder(toBuilder = true)
@RecasterAlias("io.harness.cdng.manifest.yaml.summary.ManifestStoreInfo")
public class ManifestStoreInfo {
  String branch;
  String commitId;
  String folderPath;
  String repoName;
  List<String> paths;
  String chartName;
  String chartVersion;
  String subChartPath;
  String bucketName;
  String region;
  String helmVersion;
  String storeType;
}
