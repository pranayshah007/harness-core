/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.sto;

import static io.harness.rule.OwnerRule.SERGEY;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.FunctionalTests;
import io.harness.rule.Owner;
import io.harness.testframework.framework.STOManagerExecutor;
import io.harness.testframework.restutils.NGPipelineRestUtils;

import io.restassured.RestAssured;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class STOPipelineCRUDFunctionalTest extends CategoryTest {
  private static final String accountIdentifier = "accountIdentifier";
  private static final String orgIdentifier = "orgIdentifier";
  private static final String projectIdentifier = "projectIdentifier";
  private static final String pipelineNamePlaceholder = "NAME_PLACEHOLDER";
  private static final String pipelineDescriptionPlaceholder = "DESCRIPTION_PLACEHOLDER";

  @BeforeClass
  public static void setup() throws IOException {
    RestAssured.useRelaxedHTTPSValidation();
    STOManagerExecutor.ensureSTOManager(STOPipelineCRUDFunctionalTest.class);
  }

  @Test
  @Owner(developers = SERGEY, intermittent = true)
  @Category({FunctionalTests.class})
  public void shouldTestCRUDPipelineFlow() throws IOException {
    String pipelineTemplate = IOUtils.toString(
        STOPipelineCRUDFunctionalTest.class.getResourceAsStream("pipeline.yml"), StandardCharsets.UTF_8);
    String pipelineName = UUID.randomUUID().toString();

    String pipeline = pipelineTemplate.replace(pipelineNamePlaceholder, pipelineName)
                          .replace(pipelineDescriptionPlaceholder, UUID.randomUUID().toString());

    // Create
    String id =
        NGPipelineRestUtils.createPipeline("sto", accountIdentifier, orgIdentifier, projectIdentifier, pipeline);
    assertThat(id).isNotNull().isEqualTo(pipelineName);

    // Read
    Map<String, Object> ngPipelineResponseDTO =
        NGPipelineRestUtils.readPipeline("sto", accountIdentifier, orgIdentifier, projectIdentifier, id);
    String yamlPipeline = (String) ngPipelineResponseDTO.get("yamlPipeline");
    assertThat(yamlPipeline).isEqualTo(pipeline);

    String updatedPipeline = pipelineTemplate.replace(pipelineNamePlaceholder, pipelineName)
                                 .replace(pipelineDescriptionPlaceholder, UUID.randomUUID().toString());

    // Update
    String updatedPipelineId = NGPipelineRestUtils.updatePipeline(
        "sto", accountIdentifier, orgIdentifier, projectIdentifier, id, updatedPipeline);
    assertThat(updatedPipelineId).isEqualTo(id);

    // Delete
    Boolean deletePipeline =
        NGPipelineRestUtils.deletePipeline("sto", accountIdentifier, orgIdentifier, projectIdentifier, id);
    assertThat(deletePipeline).isTrue();
  }
}
