/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.infra;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)

public interface HostConnectionTypeKind {
  String HOSTNAME = "Hostname";
  String PRIVATE_IP = "PrivateIP";
  String PUBLIC_IP = "PublicIP";
  String AZURE_ALLOWABLE_VALUES = HOSTNAME + ", " + PRIVATE_IP + ", " + PUBLIC_IP;
  String AWS_ALLOWABLE_VALUES = PUBLIC_IP + ", " + PRIVATE_IP;
}
