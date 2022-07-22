/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.exception;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.FilterCreatorException;
import io.harness.exception.PlanCreatorException;
import io.harness.pms.contracts.plan.ErrorResponse;
import io.harness.pms.contracts.plan.YamlFieldBlob;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PmsExceptionUtilsTest extends CategoryTest {
  ErrorResponse errorResponse1 =
      ErrorResponse.newBuilder().addMessages("this is an error").addMessages("this is also an error").build();
  ErrorResponse errorResponse2 =
      ErrorResponse.newBuilder().addMessages("an error").addMessages("an error again").build();
  List<ErrorResponse> errorResponses = Arrays.asList(errorResponse1, errorResponse2);

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetYamlNodeErrorInfo() throws IOException {
    String yaml1 = "step:\n"
        + "  identifier: search\n"
        + "  name: search\n"
        + "  type: Http\n"
        + "  spec:\n"
        + "    url: https://www.google.com";
    YamlField yamlField = YamlUtils.readTree(yaml1);
    YamlField stepYamlField = yamlField.getNode().getField("step");
    assertThat(stepYamlField).isNotNull();
    YamlFieldBlob stepYamlFieldBlob1 = stepYamlField.toFieldBlob();

    String yaml2 = "step:\n"
        + "  identifier: search\n"
        + "  name: search\n"
        + "  type: Http\n"
        + "  spec:\n"
        + "    url: https://www.google.com";
    YamlField yamlField2 = YamlUtils.readTree(yaml2);
    YamlField stepYamlField2 = yamlField2.getNode().getField("step");
    assertThat(stepYamlField2).isNotNull();
    YamlFieldBlob stepYamlFieldBlob2 = stepYamlField2.toFieldBlob();
    List<YamlFieldBlob> yamlFieldBlobs = Arrays.asList(stepYamlFieldBlob1, stepYamlFieldBlob2);
    List<YamlNodeErrorInfo> nodeErrorInfoList = PmsExceptionUtils.getYamlNodeErrorInfo(yamlFieldBlobs);
    assertThat(nodeErrorInfoList).hasSize(2);
    YamlNodeErrorInfo yamlNodeErrorInfo1 = nodeErrorInfoList.get(0);
    assertThat(yamlNodeErrorInfo1.getType()).isEqualTo("Http");
    assertThat(yamlNodeErrorInfo1.getIdentifier()).isEqualTo("search");
    assertThat(yamlNodeErrorInfo1.getName()).isEqualTo("step");
    YamlNodeErrorInfo yamlNodeErrorInfo2 = nodeErrorInfoList.get(1);
    assertThat(yamlNodeErrorInfo2.getType()).isEqualTo("Http");
    assertThat(yamlNodeErrorInfo2.getIdentifier()).isEqualTo("search");
    assertThat(yamlNodeErrorInfo2.getName()).isEqualTo("step");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCheckAndThrowFilterCreatorException() {
    assertThatThrownBy(() -> PmsExceptionUtils.checkAndThrowFilterCreatorException(errorResponses, new ArrayList<>()))
        .isInstanceOf(FilterCreatorException.class)
        .hasMessage("this is an error,this is also an error,an error,an error again");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void checkAndThrowPlanCreatorException() {
    assertThatThrownBy(() -> PmsExceptionUtils.checkAndThrowPlanCreatorException(errorResponses))
        .isInstanceOf(PlanCreatorException.class)
        .hasMessage("Error creating Plan: this is an error,this is also an error,an error,an error again");
  }
}
