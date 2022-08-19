/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.steps.io.PassThroughData;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@RecasterAlias("io.harness.cdng.provision.azure.beans.AzureCreateARMResourcePassThroughData")
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(CDP)
public class AzureCreateARMResourcePassThroughData implements PassThroughData {
  String templateBody;
  String parametersBody;
  @Accessors(fluent = true) boolean hasGitFiles;
}
