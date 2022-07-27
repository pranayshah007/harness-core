/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.http;

import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.http.HttpHeaderConfig;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.http.HttpStepInfo;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HttpStepInfoTest extends CategoryTest {
  private static String url = "https://www.google.com/";
  private static String method = "GET";
  private static List<TaskSelectorYaml> delegate = Collections.singletonList(new TaskSelectorYaml("delegatename"));
  private static List<HttpHeaderConfig> headers =
      Collections.singletonList(HttpHeaderConfig.builder().key("headerkey").value("headervalue").build());
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)

  public void testGetSpecParametersForNullHeaders() {
    HttpStepInfo httpStepInfo =
        HttpStepInfo.infoBuilder()
            .url(ParameterField.<String>builder().value(url).build())
            .method(ParameterField.<String>builder().value(method).build())
            .headers(null)
            .delegateSelectors(ParameterField.<List<TaskSelectorYaml>>builder().value(delegate).build())
            .build();
    SpecParameters specParameters = httpStepInfo.getSpecParameters();
    HttpStepParameters httpStepParameters = (HttpStepParameters) specParameters;
    assertThat(httpStepParameters.headers).isEmpty();
    assertThat(httpStepParameters.delegateSelectors)
        .isEqualTo(
            ParameterField.builder().value(Collections.singletonList(new TaskSelectorYaml("delegatename"))).build());
    assertThat(httpStepParameters.url.getValue()).isEqualTo(url);
    assertThat(httpStepParameters.method.getValue()).isEqualTo(method);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetSpecParametersForHeaders() {
    HttpStepInfo httpStepInfo =
        HttpStepInfo.infoBuilder()
            .url(ParameterField.<String>builder().value(url).build())
            .method(ParameterField.<String>builder().value(method).build())
            .headers(headers)
            .delegateSelectors(ParameterField.<List<TaskSelectorYaml>>builder().value(delegate).build())
            .build();
    SpecParameters specParameters = httpStepInfo.getSpecParameters();
    HttpStepParameters httpStepParameters = (HttpStepParameters) specParameters;
    assertThat(httpStepParameters.headers.size()).isEqualTo(1);
    //        getValue().get(0).getDelegateSelectors())
    assertThat(httpStepParameters.headers).isEqualTo(ImmutableMap.builder().put("headerkey", "headervalue").build());
    assertThat(httpStepParameters.delegateSelectors)
        .isEqualTo(
            ParameterField.builder().value(Collections.singletonList(new TaskSelectorYaml("delegatename"))).build());
    assertThat(httpStepParameters.url.getValue()).isEqualTo(url);
    assertThat(httpStepParameters.method.getValue()).isEqualTo(method);
  }
}
