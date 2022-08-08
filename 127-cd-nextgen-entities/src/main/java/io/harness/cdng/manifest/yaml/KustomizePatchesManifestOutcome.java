/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("kustomizePatchesManifestOutcome")
@JsonTypeName(ManifestType.KustomizePatches)
@RecasterAlias("io.harness.cdng.manifest.yaml.KustomizePatchesManifestOutcome")
@OwnedBy(CDP)
public class KustomizePatchesManifestOutcome implements ManifestOutcome {
  String identifier;
  String type = ManifestType.KustomizePatches;
  StoreConfig store;
  int order;
}
