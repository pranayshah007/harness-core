/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.MOUNIK;
import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.exception.HintException;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
public class JiraTaskNGTest extends CategoryTest {
  @Mock private JiraTaskNGHelper jiraTaskNGHelper;
  @InjectMocks
  private final JiraTaskNG jiraTaskNG =
      new JiraTaskNG(DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void testRunObjectParamsShouldThrowMotImplementedException() {
    assertThatThrownBy(() -> jiraTaskNG.run(new Object[1]))
        .hasMessage("not implemented")
        .isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void testRun() {
    JiraTaskNGResponse taskResponse = JiraTaskNGResponse.builder().build();
    when(jiraTaskNGHelper.getJiraTaskResponse(any())).thenReturn(taskResponse);
    assertThatCode(() -> jiraTaskNG.run(JiraTaskNGParameters.builder().build())).doesNotThrowAnyException();
    verify(jiraTaskNGHelper).getJiraTaskResponse(JiraTaskNGParameters.builder().build());
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void testRunFailure() {
    JiraTaskNGResponse taskResponse = JiraTaskNGResponse.builder().build();
    when(jiraTaskNGHelper.getJiraTaskResponse(any())).thenThrow(new HintException("Exception"));
    assertThatThrownBy(() -> jiraTaskNG.run(JiraTaskNGParameters.builder().build())).isInstanceOf(HintException.class);
    verify(jiraTaskNGHelper).getJiraTaskResponse(JiraTaskNGParameters.builder().build());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testGetJiraTaskNGDelegateSelectors() {
    JiraTaskNGParameters jiraTaskNGParameters = JiraTaskNGParameters.builder()
                                                    .delegateSelectors(Arrays.asList("selector1"))
                                                    .jiraConnectorDTO(JiraConnectorDTO.builder().build())
                                                    .build();
    assertThat(jiraTaskNGParameters.getDelegateSelectors()).containsExactlyInAnyOrder("selector1");
    jiraTaskNGParameters.getJiraConnectorDTO().setDelegateSelectors(ImmutableSet.of("selector2"));
    assertThat(jiraTaskNGParameters.getDelegateSelectors()).containsExactlyInAnyOrder("selector1", "selector2");
  }
}
