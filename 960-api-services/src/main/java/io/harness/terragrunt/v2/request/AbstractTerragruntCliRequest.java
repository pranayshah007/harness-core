/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.terragrunt.v2.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@OwnedBy(CDP)
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class AbstractTerragruntCliRequest {
  @Getter String workingDirectory;
  @Getter TerragruntRunType runType;
  @Getter Map<String, String> envVars;
  @Getter @Builder.Default TerragruntCliArgs args = TerragruntCliArgs.builder().build();
  @Getter Long timeoutInMillis;
  @Getter boolean skipColorLogs;
}
