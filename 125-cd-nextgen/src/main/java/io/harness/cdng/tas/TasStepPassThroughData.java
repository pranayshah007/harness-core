/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.tas;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.manifest.yaml.AutoScalerManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.TasManifestOutcome;
import io.harness.cdng.manifest.yaml.VarsManifestOutcome;
import io.harness.git.model.FetchFilesResult;
import io.harness.logging.UnitProgress;
import io.harness.manifest.CustomSourceFile;
import io.harness.pms.sdk.core.steps.io.PassThroughData;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.TypeAlias;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PCF})
@OwnedBy(CDP)
@Value
@Builder(toBuilder = true)
@TypeAlias("tasStepPassThroughData")
@RecasterAlias("io.harness.cdng.tas.TasStepPassThroughData")
public class TasStepPassThroughData implements PassThroughData {
  List<ManifestOutcome> manifestOutcomeList;
  @Setter @NonFinal TasManifestOutcome tasManifestOutcome;
  @Setter @NonFinal List<VarsManifestOutcome> varsManifestOutcomeList;
  @Setter @NonFinal AutoScalerManifestOutcome autoScalerManifestOutcome;
  Map<String, List<TasManifestFileContents>> localStoreFileMapContents;
  Map<String, FetchFilesResult> gitFetchFilesResultMap;

  // for custom source manifest and values files
  Map<String, Collection<CustomSourceFile>> customFetchContent;
  String zippedManifestFileId;
  @Setter @NonFinal List<UnitProgress> unitProgresses;
  @Setter @NonFinal List<String> commandUnits;
  @Setter @NonFinal Integer maxManifestOrder;

  @Setter @NonFinal @Builder.Default Boolean shouldExecuteCustomFetch = Boolean.FALSE;
  @Setter @NonFinal @Builder.Default Boolean shouldExecuteHarnessStoreFetch = Boolean.FALSE;
  @Setter @NonFinal @Builder.Default Boolean shouldExecuteGitStoreFetch = Boolean.FALSE;
  @Setter @NonFinal Boolean shouldCloseFetchFilesStream;
  @Setter @NonFinal Boolean shouldOpenFetchFilesStream;
  String rawScript;
  @Setter @NonFinal String repoRoot;
  @Setter @NonFinal List<String> pathsFromScript;
}
