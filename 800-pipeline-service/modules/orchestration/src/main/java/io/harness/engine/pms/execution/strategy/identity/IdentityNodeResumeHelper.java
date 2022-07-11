/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.identity;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.pms.resume.publisher.NodeResumeEventPublisher;
import io.harness.engine.pms.resume.publisher.ResumeMetadata;
import io.harness.execution.NodeExecution;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public class IdentityNodeResumeHelper {
  @Inject private NodeResumeEventPublisher nodeResumeEventPublisher;

  public void resume(
      NodeExecution nodeExecution, Map<String, ByteString> responseMap, boolean isError, String serviceName) {
    ResumeMetadata resumeMetadata = ResumeMetadata.fromNodeExecution(nodeExecution);
    nodeResumeEventPublisher.publishEventForIdentityNode(resumeMetadata, responseMap, isError, serviceName);
  }
}
