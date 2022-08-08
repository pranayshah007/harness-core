/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.identity;
import static io.harness.rule.OwnerRule.SHALINI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.pms.resume.publisher.NodeResumeEventPublisher;
import io.harness.engine.pms.resume.publisher.ResumeMetadata;
import io.harness.execution.NodeExecution;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class IdentityNodeResumeHelperTest extends OrchestrationTestBase {
  @Mock private NodeResumeEventPublisher nodeResumeEventPublisher;
  @InjectMocks IdentityNodeResumeHelper identityNodeResumeHelper;

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testResume() {
    ResumeMetadata resumeMetadata = ResumeMetadata.builder().build();
    MockedStatic<ResumeMetadata> utilities = Mockito.mockStatic(ResumeMetadata.class);
    utilities.when(() -> ResumeMetadata.fromNodeExecution(any(NodeExecution.class))).thenReturn(resumeMetadata);
    identityNodeResumeHelper.resume(NodeExecution.builder().build(), new HashMap<>(), false, "");
    verify(nodeResumeEventPublisher, times(1))
        .publishEventForIdentityNode(any(ResumeMetadata.class), any(Map.class), anyBoolean(), anyString());
  }
}
