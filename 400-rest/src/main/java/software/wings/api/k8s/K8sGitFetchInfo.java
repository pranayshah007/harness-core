/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SweepingOutput;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import lombok.Builder;
/**
 * Class for sweeping Output to save commitIds  for GitFetch
 */
@Builder
@JsonTypeName("K8sGitFetchInfo")
@TargetModule(HarnessModule._957_CG_BEANS)
@OwnedBy(CDP)
public class K8sGitFetchInfo extends HashMap<String, K8sGitInfo> implements SweepingOutput {
  public static final String SWEEPING_OUTPUT_NAME_PREFIX = "k8sManifest";
  @Override
  public String getType() {
    return "K8sGitFetchInfo";
  }
}
