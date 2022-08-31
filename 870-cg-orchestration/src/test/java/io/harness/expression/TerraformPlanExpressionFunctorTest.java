/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.provision.TerraformConstants.TF_APPLY_VAR_NAME;
import static io.harness.provision.TerraformConstants.TF_DESTROY_VAR_NAME;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.terraform.TerraformPlanParam;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.FileMetadata;
import io.harness.exception.FunctorException;
import io.harness.rule.Owner;
import io.harness.terraform.expression.TerraformPlanExpressionInterface;

import software.wings.service.intfc.FileService;

import java.util.function.Function;
import org.assertj.core.api.ThrowableAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class TerraformPlanExpressionFunctorTest extends CategoryTest {
  private static final int FUNCTOR_TOKEN = 1234567890;
  @Mock private Function<String, TerraformPlanParam> obtainTfPlanFunction;
  @Mock private FileService fileService;

  private TerraformPlanExpressionFunctor expressionFunctor;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    this.expressionFunctor = TerraformPlanExpressionFunctor.builder()
                                 .obtainTfPlanFunction(this.obtainTfPlanFunction)
                                 .expressionFunctorToken(FUNCTOR_TOKEN)
                                 .fileService(fileService)
                                 .build();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testJsonFilePath() {
    preparePlanFileForTest("applyFileId", TF_APPLY_VAR_NAME);
    preparePlanFileForTest("destroyFileId", TF_DESTROY_VAR_NAME);

    assertThat(expressionFunctor.jsonFilePath())
        .isEqualTo(
            format(TerraformPlanExpressionInterface.DELEGATE_EXPRESSION, "applyFileId", FUNCTOR_TOKEN, "jsonFilePath"));
    assertThat(expressionFunctor.destroy.jsonFilePath())
        .isEqualTo(format(
            TerraformPlanExpressionInterface.DELEGATE_EXPRESSION, "destroyFileId", FUNCTOR_TOKEN, "jsonFilePath"));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReuseCachedValue() {
    preparePlanFileForTest("applyFileIdOriginal", TF_APPLY_VAR_NAME);
    preparePlanFileForTest("destroyFileIdOriginal", TF_DESTROY_VAR_NAME);

    expressionFunctor.jsonFilePath();
    expressionFunctor.destroy.jsonFilePath();

    preparePlanFileForTest("applyFileIdUpdated", TF_APPLY_VAR_NAME);
    preparePlanFileForTest("destroyFileIdUpdated", TF_DESTROY_VAR_NAME);

    assertThat(expressionFunctor.jsonFilePath())
        .isEqualTo(format(TerraformPlanExpressionInterface.DELEGATE_EXPRESSION, "applyFileIdOriginal", FUNCTOR_TOKEN,
            "jsonFilePath"));
    assertThat(expressionFunctor.destroy.jsonFilePath())
        .isEqualTo(format(TerraformPlanExpressionInterface.DELEGATE_EXPRESSION, "destroyFileIdOriginal", FUNCTOR_TOKEN,
            "jsonFilePath"));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testJsonFilePathNoPlan() {
    String jsonPath = expressionFunctor.jsonFilePath();
    assertThat(jsonPath).isEqualTo(null);
    String destroyJsonPath = expressionFunctor.jsonFilePath();
    assertThat(destroyJsonPath).isEqualTo(null);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testJsonFilePathNoFileId() {
    doReturn(TerraformPlanParam.builder().build()).when(obtainTfPlanFunction).apply(TF_APPLY_VAR_NAME);
    doReturn(TerraformPlanParam.builder().build()).when(obtainTfPlanFunction).apply(TF_DESTROY_VAR_NAME);

    assertExceptionMessage(()
                               -> expressionFunctor.jsonFilePath(),
        "Invalid usage of terraform plan functor. Missing tfPlanJsonFileId in terraform plan param 'terraformApply'");
    assertExceptionMessage(()
                               -> expressionFunctor.destroy.jsonFilePath(),
        "Invalid usage of terraform plan functor. Missing tfPlanJsonFileId in terraform plan param 'terraformDestroy'");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testJsonFilePathNoFileExists() {
    doReturn(TerraformPlanParam.builder().tfPlanJsonFileId("applyFileId").build())
        .when(obtainTfPlanFunction)
        .apply(TF_APPLY_VAR_NAME);
    doReturn(TerraformPlanParam.builder().tfPlanJsonFileId("destroyFileId").build())
        .when(obtainTfPlanFunction)
        .apply(TF_DESTROY_VAR_NAME);
    assertExceptionMessage(()
                               -> expressionFunctor.jsonFilePath(),
        "File with id 'applyFileId' doesn't exist. Json representation of terraform plan isn't available after it was applied");
    assertExceptionMessage(()
                               -> expressionFunctor.destroy.jsonFilePath(),
        "File with id 'destroyFileId' doesn't exist. Json representation of terraform plan isn't available after it was applied");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testJsonFilePathNoFileExistsException() {
    doReturn(TerraformPlanParam.builder().tfPlanJsonFileId("applyFileId").build())
        .when(obtainTfPlanFunction)
        .apply(TF_APPLY_VAR_NAME);
    doReturn(TerraformPlanParam.builder().tfPlanJsonFileId("destroyFileId").build())
        .when(obtainTfPlanFunction)
        .apply(TF_DESTROY_VAR_NAME);
    doThrow(new RuntimeException("No file found"))
        .when(fileService)
        .getFileMetadata("applyFileId", FileBucket.TERRAFORM_PLAN_JSON);
    doThrow(new RuntimeException("No file found"))
        .when(fileService)
        .getFileMetadata("destroyFileId", FileBucket.TERRAFORM_PLAN_JSON);
    assertExceptionMessage(()
                               -> expressionFunctor.jsonFilePath(),
        "File with id 'applyFileId' doesn't exist. Json representation of terraform plan isn't available after it was applied");
    assertExceptionMessage(()
                               -> expressionFunctor.destroy.jsonFilePath(),
        "File with id 'destroyFileId' doesn't exist. Json representation of terraform plan isn't available after it was applied");
  }

  private void preparePlanFileForTest(String filedId, String planName) {
    TerraformPlanParam tfPlan = TerraformPlanParam.builder().tfPlanJsonFileId(filedId).build();
    FileMetadata fileMetadata = FileMetadata.builder().build();

    doReturn(tfPlan).when(obtainTfPlanFunction).apply(planName);
    doReturn(fileMetadata).when(fileService).getFileMetadata(filedId, FileBucket.TERRAFORM_PLAN_JSON);
  }

  private void assertExceptionMessage(ThrowableAssert.ThrowingCallable callable, String expectedMessage) {
    assertThatThrownBy(callable).matches(exception -> {
      assertThat(exception).isInstanceOf(FunctorException.class);
      assertThat(((FunctorException) exception).getReason()).contains(expectedMessage);
      return true;
    });
  }
}
