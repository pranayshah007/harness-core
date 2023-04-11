/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.retry.RetryExecutionMetadata;
import io.harness.execution.ExpressionDetailResponse;
import io.harness.execution.ExpressionDetails;
import io.harness.execution.ExpressionTestDetails;
import io.harness.execution.ExpressionTestResponse;
import io.harness.execution.PlanExecution;
import io.harness.execution.expansion.ExpressionTestService;
import io.harness.execution.expansion.ExpressionTestServiceImpl;
import io.harness.gitx.USER_FLOW;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.RunStageRequestDTO;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.stages.StageExecutionResponse;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ThreadOperationContextHelper;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.PIE_GET_FILE_CONTENT_ONLY;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@OwnedBy(PIPELINE)
public class ExpressionTestServiceTest extends CategoryTest {
  ExpressionTestServiceImpl expressionTestService = new ExpressionTestServiceImpl();

  @Before
  public void setUp(){

  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void test() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode jsonNode = objectMapper.readTree(getResource("cfStageSchema.json"));
    String exp = "pipeline.stages.cd.spec.execution.steps.ShellScript_1.name";
    List<ExpressionDetails> response = expressionTestService.getAllExpressions(jsonNode,exp);
    assertNotNull(response);
  }

  private String getResource(String path) throws IOException {
    return IOUtils.resourceToString(path, StandardCharsets.UTF_8, this.getClass().getClassLoader());
  }
}
