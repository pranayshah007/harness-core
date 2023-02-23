/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.asg.manifest;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
public class AsgManifestHandlerChainState {
  private String asgName;
  private String launchTemplateVersion;
  private AutoScalingGroup autoScalingGroup;
  private Map<String, List<String>> asgManifestsDataForRollback = new HashMap<>();
  private String newAsgName;
  private String executionStrategy;
}
