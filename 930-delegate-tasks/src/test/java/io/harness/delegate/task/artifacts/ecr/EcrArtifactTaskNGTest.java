/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.ecr;

import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
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

import org.apache.commons.lang3.NotImplementedException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.PIPELINE)
public class EcrArtifactTaskNGTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private EcrArtifactTaskHelper ecrArtifactTaskHelper;
  @InjectMocks
  private EcrArtifactTaskNG ecrArtifactTaskNG =
      new EcrArtifactTaskNG(DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testRunWithObjectParams() {
    assertThatThrownBy(() -> ecrArtifactTaskNG.run(new Object[10]))
        .hasMessage("not implemented")
        .isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testRunWithTaskParamsSuccess() {
    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder().build();
    when(ecrArtifactTaskHelper.getArtifactCollectResponse(any())).thenReturn(artifactTaskResponse);
    assertThat(ecrArtifactTaskNG.run(ArtifactTaskParameters.builder().build())).isEqualTo(artifactTaskResponse);

    verify(ecrArtifactTaskHelper).getArtifactCollectResponse(ArtifactTaskParameters.builder().build());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testRunWithTaskParamsFailure() {
    String message = "General Exception";
    when(ecrArtifactTaskHelper.getArtifactCollectResponse(any())).thenThrow(new GeneralException(message));
    assertThatThrownBy(() -> ecrArtifactTaskNG.run(ArtifactTaskParameters.builder().build()))
        .isInstanceOf(GeneralException.class)
        .hasMessage(message);

    verify(ecrArtifactTaskHelper).getArtifactCollectResponse(ArtifactTaskParameters.builder().build());
  }
}
