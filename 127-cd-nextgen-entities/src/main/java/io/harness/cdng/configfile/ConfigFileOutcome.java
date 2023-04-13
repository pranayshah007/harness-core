/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.WithIdentifier;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Value
@Builder
@TypeAlias("configFileOutcome")
@JsonTypeName(ManifestType.K8Manifest)
@FieldNameConstants(innerTypeName = "ConfigFileOutcomeKeys")
@RecasterAlias("io.harness.cdng.configfile.ConfigFileOutcome")
public class ConfigFileOutcome implements Outcome, WithIdentifier {
  String identifier;
  StoreConfig store;
  List<ConfigGitFile> gitFiles;
  int order;
}
