/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.outcome.HelmChartOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.k8s.model.HelmVersion;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Value
@Builder(toBuilder = true)
@OwnedBy(CDP)
@JsonTypeName(ManifestType.HelmChart)
@TypeAlias("helmChartManifestOutcome")
@FieldNameConstants(innerTypeName = "HelmChartManifestOutcomeKeys")
@RecasterAlias("io.harness.cdng.manifest.yaml.HelmChartManifestOutcome")
public class HelmChartManifestOutcome implements ManifestOutcome {
  String identifier;
  String type = ManifestType.HelmChart;
  StoreConfig store;
  ParameterField<String> chartName;
  ParameterField<String> chartVersion;
  ParameterField<Boolean> skipResourceVersioning;
  ParameterField<Boolean> enableDeclarativeRollback;
  HelmVersion helmVersion;
  List<HelmManifestCommandFlag> commandFlags;
  ParameterField<List<String>> valuesPaths;
  ParameterField<String> subChartPath;
  ParameterField<Boolean> fetchHelmChartMetadata;
  HelmChartOutcome helm;

  public ParameterField<List<String>> getValuesPaths() {
    if (!(getParameterFieldValue(this.valuesPaths) instanceof List)) {
      return ParameterField.createValueField(Collections.emptyList());
    }
    return this.valuesPaths;
  }
}
