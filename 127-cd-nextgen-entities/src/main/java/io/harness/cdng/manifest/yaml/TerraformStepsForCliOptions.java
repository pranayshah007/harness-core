/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

@OwnedBy(CDP)
public interface TerraformStepsForCliOptions {
  String PLAN = "Plan";
  String APPLY = "Apply";
  String DESTROY = "Destroy";
  String ROLLBACK = "Rollback";

  Set<String> TerraformYamlConfigAndCloudCli = ImmutableSet.of("configuration", "cloudCliConfiguration");
  Set<String> TerraformYamlConfig = ImmutableSet.of("configuration");
}
