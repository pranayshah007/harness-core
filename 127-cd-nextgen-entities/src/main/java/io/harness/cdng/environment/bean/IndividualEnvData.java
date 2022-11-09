/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.bean;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDC)
public class IndividualEnvData {
  String envName;
  String envRef;
  boolean deployToAll;
  Set<String> gitOpsClusterRefs;
}
