/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.jira;

import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class JiraStepUtilsTest extends CategoryTest {
  @Mock private NGLogCallback mockNgLogCallback;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testProcessJiraFieldsInParameters() {
    doNothing().when(mockNgLogCallback).saveExecutionLog(any(), any());
    assertThat(JiraStepUtils.processJiraFieldsInParameters(null, mockNgLogCallback)).isEmpty();
    assertThat(JiraStepUtils.processJiraFieldsInParameters(Collections.emptyMap(), mockNgLogCallback)).isEmpty();

    assertThatThrownBy(
        ()
            -> JiraStepUtils.processJiraFieldsInParameters(
                ImmutableMap.of("a", ParameterField.createValueField("va"), "b", ParameterField.createValueField(null),
                    "c", ParameterField.createExpressionField(true, "<+abc>", null, true)),
                mockNgLogCallback))
        .hasMessage("Field [c] has invalid jira field value");

    Map<String, String> fields = JiraStepUtils.processJiraFieldsInParameters(
        ImmutableMap.of("a", ParameterField.createValueField("va"), "b", ParameterField.createValueField(null)),
        mockNgLogCallback);
    assertThat(fields.size()).isEqualTo(1);
    assertThat(fields.get("a")).isEqualTo("va");
  }
}
