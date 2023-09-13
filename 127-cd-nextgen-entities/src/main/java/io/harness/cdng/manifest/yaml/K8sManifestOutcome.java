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
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.summary.ManifestStoreInfo;
import io.harness.cdng.manifest.yaml.summary.ManifestStoreInfo.ManifestStoreInfoBuilder;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@OwnedBy(CDP)
@TypeAlias("k8sManifestOutcome")
@JsonTypeName(ManifestType.K8Manifest)
@FieldNameConstants(innerTypeName = "K8sManifestOutcomeKeys")
@RecasterAlias("io.harness.cdng.manifest.yaml.K8sManifestOutcome")
public class K8sManifestOutcome implements ManifestOutcome {
  String identifier;
  String type = ManifestType.K8Manifest;
  StoreConfig store;
  ParameterField<Boolean> skipResourceVersioning;
  ParameterField<Boolean> enableDeclarativeRollback;
  ParameterField<List<String>> valuesPaths;

  public ParameterField<List<String>> getValuesPaths() {
    if (!(getParameterFieldValue(this.valuesPaths) instanceof List)) {
      return ParameterField.createValueField(Collections.emptyList());
    }
    return this.valuesPaths;
  }
  @Override
  public Optional<ManifestStoreInfo> toManifestStoreInfo() {
    ManifestStoreInfoBuilder manifestInfoBuilder = ManifestStoreInfo.builder();
    store.populateManifestStoreInfo(manifestInfoBuilder);
    return Optional.of(manifestInfoBuilder.build());
  }
}
