/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.hooks.steps.ServiceHooksOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftParamManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.delegate.task.helm.HelmFetchFileResult;
import io.harness.delegate.task.localstore.LocalStoreFetchFilesResult;
import io.harness.delegate.task.localstore.ManifestFiles;
import io.harness.manifest.CustomSourceFile;
import io.harness.pms.sdk.core.steps.io.PassThroughData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Value
@Builder(toBuilder = true)
@TypeAlias("k8sStepPassThroughData")
@RecasterAlias("io.harness.cdng.k8s.K8sStepPassThroughData")
public class K8sStepPassThroughData implements PassThroughData {
  ManifestOutcome manifestOutcome;
  List<ManifestOutcome> manifestOutcomeList;
  InfrastructureOutcome infrastructure;
  Map<String, HelmFetchFileResult> helmValuesFileMapContents;
  Map<String, LocalStoreFetchFilesResult> localStoreFileMapContents;
  String helmValuesFileContent;
  List<ManifestFiles> manifestFiles;
  ServiceHooksOutcome serviceHooksOutcome;
  // for custom source manifest and values files
  Map<String, Collection<CustomSourceFile>> customFetchContent;
  String zippedManifestFileId;

  @Setter @NonFinal boolean shouldCloseFetchFilesStream;
  @Setter @NonFinal Boolean shouldOpenFetchFilesStream;
  Set<String> manifestStoreTypeVisited;

  public List<ValuesManifestOutcome> getValuesManifestOutcomes() {
    if (isEmpty(manifestOutcomeList)) {
      return Collections.emptyList();
    }
    List<ValuesManifestOutcome> valuesOutcomeList = new ArrayList<>();
    for (ManifestOutcome manifestOutcome : manifestOutcomeList) {
      if (ManifestType.VALUES.equals(manifestOutcome.getType())) {
        valuesOutcomeList.add((ValuesManifestOutcome) manifestOutcome);
      }
    }
    return valuesOutcomeList;
  }

  public List<OpenshiftParamManifestOutcome> getOpenShiftParamsOutcomes() {
    if (isEmpty(manifestOutcomeList)) {
      return Collections.emptyList();
    }
    List<OpenshiftParamManifestOutcome> openshiftParamManifestOutcomes = new ArrayList<>();
    for (ManifestOutcome manifestOutcome : manifestOutcomeList) {
      if (ManifestType.OpenshiftParam.equals(manifestOutcome.getType())) {
        openshiftParamManifestOutcomes.add((OpenshiftParamManifestOutcome) manifestOutcome);
      }
    }
    return openshiftParamManifestOutcomes;
  }

  public void updateOpenFetchFilesStreamStatus() {
    this.shouldOpenFetchFilesStream = this.shouldOpenFetchFilesStream == null;
  }

  public void updateNativeHelmCloseFetchFilesStreamStatus(Set<String> manifestStoreTypeList) {
    this.manifestStoreTypeVisited.addAll(manifestStoreTypeList);
    boolean updatedCloseFetchFilesStreamStatus = true;
    if (!this.shouldCloseFetchFilesStream) {
      for (ManifestOutcome individualManifestOutcome : this.getManifestOutcomeList()) {
        updatedCloseFetchFilesStreamStatus = updatedCloseFetchFilesStreamStatus
            && this.manifestStoreTypeVisited.contains(individualManifestOutcome.getStore().getKind());
      }
    }
    this.shouldCloseFetchFilesStream = updatedCloseFetchFilesStreamStatus;
  }
}
