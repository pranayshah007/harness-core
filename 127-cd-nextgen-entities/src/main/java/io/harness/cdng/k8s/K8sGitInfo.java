/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
/**
 * Used to save commitId from Git Fetch and later used in expression to get commitId
 */
@Data
@Builder
@JsonTypeName("K8sGitConfigMapInfo")
@OwnedBy(CDP)
@TargetModule(HarnessModule._957_CG_BEANS)
@RecasterAlias("io.harness.cdng.k8s.K8sGitInfo")
public class K8sGitInfo {
  private String commitId;
}
