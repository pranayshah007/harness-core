/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.asg.manifest;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDP)
public interface AsgManifestType {
  String AsgLaunchTemplate = "AsgLaunchTemplate";
  String AsgConfiguration = "AsgConfiguration";
  String AsgScalingPolicy = "AsgScalingPolicy";
  String AsgScheduledUpdateGroupAction = "AsgScheduledUpdateGroupAction";
  String AsgInstanceRefresh = "AsgInstanceRefresh";
  String AsgSwapService = "AsgSwapService";
  String AsgUserData = "userData";
}
