/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.helm;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * This is generated from harness specific section of
 * value.yaml read from git repo cpnfigured by client
 *
 * harness:
 *    helm:
 *        chart:
 *             url: google.com
 *        timeout:10  // this is a pseudo field
 */
@Data
@Builder
@AllArgsConstructor
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(HarnessTeam.CDP)
public class HarnessHelmDeployConfig {
  private HelmDeployChartSpec helmDeployChartSpec;
}
