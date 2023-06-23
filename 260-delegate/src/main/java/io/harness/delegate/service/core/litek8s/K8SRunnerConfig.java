/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.litek8s;

import lombok.Data;

@Data
public class K8SRunnerConfig {
  private final String namespace;
  private final String delegateName;
  private final String accountId;
  private final String logServiceUrl;

  // TODO: possible to pull from private repo or have CI/CD/STO specific addon/LE (CI Addon has TI & STO specific code).
  // It should belong to runner startup config not PL because it's a property of a runner not our SaaS system, but that
  // means you need to reconfigure runner to upgrade. Maybe through upgrade (e.g. put in config map that upgrader
  // updates) or find a way to configure from UI? Extra considerations for imagePullSecrets for private repos
  private final String ciAddonImage = "harness/ci-addon:1.16.7";
  private final String leImage = "harness/ci-lite-engine:1.16.7";
}
