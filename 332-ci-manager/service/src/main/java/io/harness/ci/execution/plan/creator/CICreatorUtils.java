/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.sto.STOStepType;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Set;

@OwnedBy(HarnessTeam.CI)
public class CICreatorUtils {
  public static Set<String> getSupportedSteps() {
    // These are internal steps does not need to be in V2
    return Sets.newHashSet();
  }

  public static Set<String> getSupportedStepsV2() {
    HashSet<String> steps = Sets.newHashSet("Run", "SaveCacheS3", "RunTests", "liteEngineTask", "BuildAndPushACR",
        "BuildAndPushGCR", "BuildAndPushECR", "BuildAndPushDockerRegistry", "Plugin", "RestoreCacheGCS",
        "RestoreCacheS3", "SaveCacheGCS", "S3Upload", "GCSUpload", "ArtifactoryUpload", "Security", "GitClone",
        "Background", "Action", "Bitrise", "script", "plugin", "test", "background", "bitrise", "action");

    steps.addAll(STOStepType.getSupportedSteps());

    return steps;
  }
}
