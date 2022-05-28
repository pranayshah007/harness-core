/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.delegate.task.helm.HelmFetchFileResult;
import io.harness.pms.sdk.core.steps.io.PassThroughData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Value
@Builder
@TypeAlias("HelmStepPassThroughData")
@RecasterAlias("io.harness.cdng.helm.HelmStepPassThroughData")
public class NativeHelmStepPassThroughData implements PassThroughData {
  ManifestOutcome helmChartManifestOutcome;
  List<ManifestOutcome> manifestOutcomeList;
  InfrastructureOutcome infrastructure;
  Map<String, HelmFetchFileResult> helmValuesFileMapContents;
  String helmValuesFileContent;

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
}
