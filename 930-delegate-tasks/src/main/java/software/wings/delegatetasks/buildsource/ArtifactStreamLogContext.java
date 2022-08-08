/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.buildsource;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.logging.AutoLogContext;

import com.google.common.collect.ImmutableMap;

@OwnedBy(CDC)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ArtifactStreamLogContext extends AutoLogContext {
  public static final String ID = "artifactStreamId";
  public static final String ARTIFACT_STREAM_TYPE = "artifactStreamType";

  public ArtifactStreamLogContext(String artifactStreamId, String artifactStreamType, OverrideBehavior behavior) {
    super(ImmutableMap.of(ID, artifactStreamId, ARTIFACT_STREAM_TYPE, artifactStreamType), behavior);
  }

  public ArtifactStreamLogContext(String artifactStreamId, OverrideBehavior behavior) {
    super(ImmutableMap.of(ID, artifactStreamId), behavior);
  }
}
