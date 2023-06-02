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
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.kinds.KustomizeManifestCommandFlag;
import io.harness.cdng.manifest.yaml.kinds.kustomize.OverlayConfiguration;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@OwnedBy(CDP)
@JsonTypeName(ManifestType.Kustomize)
@TypeAlias("kustomizeManifestOutcome")
@FieldNameConstants(innerTypeName = "KustomizeManifestOutcomeKeys")
@RecasterAlias("io.harness.cdng.manifest.yaml.KustomizeManifestOutcome")
public class KustomizeManifestOutcome implements ManifestOutcome {
  String identifier;
  String type = ManifestType.Kustomize;
  StoreConfig store;
  ParameterField<OverlayConfiguration> overlayConfiguration;
  ParameterField<String> pluginPath;
  ParameterField<Boolean> skipResourceVersioning;
  ParameterField<Boolean> enableDeclarativeRollback;
  ParameterField<List<String>> patchesPaths;
  List<KustomizeManifestCommandFlag> commandFlags;

  public ParameterField<List<String>> getPatchesPaths() {
    if (!(getParameterFieldValue(this.patchesPaths) instanceof List)) {
      return ParameterField.createValueField(Collections.emptyList());
    }
    return this.patchesPaths;
  }
}
