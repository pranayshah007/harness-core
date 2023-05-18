/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.EngineFunctorException;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.project.remote.ProjectClient;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;

@OwnedBy(HarnessTeam.PIPELINE)
@PrepareForTest({SafeHttpCall.class})
public class ProjectFunctorTest extends CategoryTest {
  @Mock private ProjectClient projectClient;
  @InjectMocks private ProjectFunctor projectFunctor;
  private Ambiance ambiance = Ambiance.newBuilder().build();
  private Ambiance ambiance1 = Ambiance.newBuilder()
                                   .putSetupAbstractions("accountId", "accountId")
                                   .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                                   .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                                   .build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testBind() throws IOException {
    MockedStatic<SafeHttpCall> aStatic = Mockito.mockStatic(SafeHttpCall.class);

    Optional<ProjectResponse> resData =
        Optional.of(ProjectResponse.builder().project(ProjectDTO.builder().build()).build());
    ResponseDTO responseDTO = ResponseDTO.newResponse();
    responseDTO.setData(resData);

    on(projectFunctor).set("ambiance", ambiance);
    assertNull(projectFunctor.bind());
    on(projectFunctor).set("ambiance", ambiance1);

    when(projectClient.getProject(anyString(), anyString(), anyString())).thenReturn(null);
    // Should throw exception due to NPE
    assertThatThrownBy(() -> projectFunctor.bind()).isInstanceOf(EngineFunctorException.class);
    aStatic.when(() -> SafeHttpCall.execute(any())).thenReturn(responseDTO);
    assertEquals(projectFunctor.bind(), resData.get().getProject());
  }
}
