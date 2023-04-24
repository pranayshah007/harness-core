/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;

import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.service.intfc.WorkflowService;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactStreamServiceBindingServiceImplTest extends WingsBaseTest {
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private ServiceVariableService serviceVariableService;
  @Mock private AppService appService;
  @Mock private ArtifactService artifactService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private WorkflowService workflowService;
  @Inject @InjectMocks private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldThrowZombieExceptionForService() {
    when(artifactStreamService.get(eq(ARTIFACT_STREAM_ID))).thenReturn(null);
    assertThatThrownBy(() -> artifactStreamServiceBindingService.getService(APP_ID, ARTIFACT_STREAM_ID, true))
        .isInstanceOf(InvalidArtifactServerException.class);
    verify(artifactStreamService).delete(eq(APP_ID), eq(ARTIFACT_STREAM_ID));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldThrowZombieExceptionForServiceId() {
    when(artifactStreamService.get(eq(ARTIFACT_STREAM_ID))).thenReturn(null);
    assertThatThrownBy(() -> artifactStreamServiceBindingService.getServiceId(APP_ID, ARTIFACT_STREAM_ID, true))
        .isInstanceOf(InvalidArtifactServerException.class);
    verify(artifactStreamService).delete(eq(APP_ID), eq(ARTIFACT_STREAM_ID));
  }
}
