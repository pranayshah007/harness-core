/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.nexus;

import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.exception.GeneralException;
import io.harness.rule.Owner;

import java.io.IOException;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class NexusArtifactTaskNGTest extends CategoryTest {
  @Mock NexusArtifactTaskHelper nexusArtifactTaskHelper;
  @InjectMocks
  private NexusArtifactTaskNG nexusArtifactTaskNG =
      new NexusArtifactTaskNG(DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testRunObjectParams() throws IOException {
    assertThatThrownBy(() -> nexusArtifactTaskNG.run(new Object[10]))
        .hasMessage("not implemented")
        .isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testRun() {
    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder().build();
    when(nexusArtifactTaskHelper.getArtifactCollectResponse(any())).thenReturn(artifactTaskResponse);
    assertThat(nexusArtifactTaskNG.run(ArtifactTaskParameters.builder().build())).isEqualTo(artifactTaskResponse);

    verify(nexusArtifactTaskHelper).getArtifactCollectResponse(ArtifactTaskParameters.builder().build());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testRunDoThrowException() {
    String message = "General Exception";
    when(nexusArtifactTaskHelper.getArtifactCollectResponse(any())).thenThrow(new GeneralException(message));
    assertThatThrownBy(() -> nexusArtifactTaskNG.run(ArtifactTaskParameters.builder().build()))
        .isInstanceOf(GeneralException.class)
        .hasMessage(message);

    verify(nexusArtifactTaskHelper).getArtifactCollectResponse(ArtifactTaskParameters.builder().build());
  }
}
