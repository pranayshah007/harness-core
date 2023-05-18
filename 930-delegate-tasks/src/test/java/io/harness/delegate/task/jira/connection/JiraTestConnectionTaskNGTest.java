/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.jira.connection;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.MOUNIK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.jira.JiraConnectionTaskParams;
import io.harness.delegate.beans.connector.jira.connection.JiraTestConnectionTaskNGResponse;
import io.harness.delegate.task.jira.JiraTaskNGHelper;
import io.harness.delegate.task.jira.JiraTaskNGResponse;
import io.harness.exception.HintException;
import io.harness.jira.JiraClient;
import io.harness.rule.Owner;

import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
public class JiraTestConnectionTaskNGTest extends CategoryTest {
  @Mock private JiraTaskNGHelper jiraTaskNGHelper;
  @Mock private JiraClient jiraClient;
  @InjectMocks
  private final JiraTestConnectionTaskNG jiraTestConnectionTaskNG = new JiraTestConnectionTaskNG(
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void testRunObjectParamsShouldThrowMotImplementedException() {
    assertThatThrownBy(() -> jiraTestConnectionTaskNG.run(new Object[1]))
        .hasMessage("This method is deprecated")
        .isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void testRun() {
    JiraTaskNGResponse taskResponse = JiraTaskNGResponse.builder().build();
    when(jiraTaskNGHelper.getJiraTaskResponse(any())).thenReturn(taskResponse);
    DelegateResponseData response = jiraTestConnectionTaskNG.run(JiraConnectionTaskParams.builder().build());

    assertThat(((JiraTestConnectionTaskNGResponse) response).getCanConnect()).isEqualTo(true);

    verify(jiraTaskNGHelper).getJiraTaskResponse(any());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void testRunWhenCantConnect() {
    when(jiraTaskNGHelper.getJiraTaskResponse(any())).thenThrow(new RuntimeException("exception"));
    DelegateResponseData response = jiraTestConnectionTaskNG.run(JiraConnectionTaskParams.builder().build());

    assertThat(((JiraTestConnectionTaskNGResponse) response).getCanConnect()).isEqualTo(false);

    verify(jiraTaskNGHelper).getJiraTaskResponse(any());
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void testRunWhenCantConnectWithHintException() {
    when(jiraTaskNGHelper.getJiraTaskResponse(any())).thenThrow(new HintException("exception"));
    assertThatThrownBy(() -> jiraTestConnectionTaskNG.run(JiraConnectionTaskParams.builder().build()))
        .isInstanceOf(HintException.class);
    verify(jiraTaskNGHelper).getJiraTaskResponse(any());
  }
}
