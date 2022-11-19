/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.commons.ManifestType;
import io.harness.cdng.commons.StoreConfig;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("ecsScalableTargetDefinitionManifestOutcome")
@JsonTypeName(ManifestType.EcsScalableTargetDefinition)
@RecasterAlias("io.harness.cdng.manifest.yaml.EcsScalableTargetDefinitionManifestOutcome")
public class EcsScalableTargetDefinitionManifestOutcome implements ManifestOutcome {
  String identifier;
  String type = ManifestType.EcsScalableTargetDefinition;
  StoreConfig store;
  int order;
}
