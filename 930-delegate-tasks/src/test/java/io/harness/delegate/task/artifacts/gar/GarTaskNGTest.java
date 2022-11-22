/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.gar;

import static io.harness.rule.OwnerRule.vivekveman;

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
import io.harness.delegate.task.artifacts.googleartifactregistry.GARArtifactTaskHelper;
import io.harness.delegate.task.artifacts.googleartifactregistry.GARArtifactTaskNG;
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

@OwnedBy(HarnessTeam.CDC)
public class GarTaskNGTest extends CategoryTest {
  @Mock GARArtifactTaskHelper garArtifactTaskHelper;
  @InjectMocks
  private GARArtifactTaskNG garArtifactTaskNG =
      new GARArtifactTaskNG(DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testRunObjectParams() throws IOException {
    assertThatThrownBy(() -> garArtifactTaskNG.run(new Object[10]))
        .hasMessage("not implemented")
        .isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testRun() throws IOException {
    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder().build();
    when(garArtifactTaskHelper.getArtifactCollectResponse(any())).thenReturn(artifactTaskResponse);
    assertThat(garArtifactTaskNG.run(ArtifactTaskParameters.builder().build())).isEqualTo(artifactTaskResponse);

    verify(garArtifactTaskHelper).getArtifactCollectResponse(ArtifactTaskParameters.builder().build());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testRunDoThrowException() throws IOException {
    String message = "General Exception";
    when(garArtifactTaskHelper.getArtifactCollectResponse(any())).thenThrow(new GeneralException(message));
    assertThatThrownBy(() -> garArtifactTaskNG.run(ArtifactTaskParameters.builder().build()))
        .isInstanceOf(GeneralException.class)
        .hasMessage(message);

    verify(garArtifactTaskHelper).getArtifactCollectResponse(ArtifactTaskParameters.builder().build());
  }
}
