/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.helm.deployconfig;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * This is generated from harness specific section of
 * value.yaml read from git repo configured by client
 * Harness
 *   helm:
 *      chart:
 *         url: www.google.com
 *         name: name
 *         version: 1.0.1
 *
 */
@Data
@Builder
@AllArgsConstructor
@OwnedBy(CDP)
public class HelmDeployChartSpec {
  private String url;
  private String name;
  private String version;
}
