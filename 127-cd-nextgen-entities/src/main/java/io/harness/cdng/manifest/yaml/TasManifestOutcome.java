/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.pcf.model.CfCliVersionNG;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@OwnedBy(CDP)
@TypeAlias("tasManifestOutcome")
@JsonTypeName(ManifestType.TAS_MANIFEST)
@FieldNameConstants(innerTypeName = "TasManifestOutcomeKeys")
@RecasterAlias("io.harness.cdng.manifest.yaml.TasManifestOutcome")
public class TasManifestOutcome implements ManifestOutcome {
  String identifier;
  String type = ManifestType.TAS_MANIFEST;
  StoreConfig store;
  CfCliVersionNG cfCliVersion;
  ParameterField<List<String>> varsPaths;
  ParameterField<List<String>> autoScalerPath;
}
