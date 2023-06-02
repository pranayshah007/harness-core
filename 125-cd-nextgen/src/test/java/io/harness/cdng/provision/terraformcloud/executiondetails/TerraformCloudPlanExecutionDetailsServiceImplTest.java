/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud.executiondetails;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.cdng.provision.terraform.executions.TerraformCloudPlanExecutionDetails;
import io.harness.exception.InvalidArgumentsException;
import io.harness.repositories.executions.TerraformCloudPlanExecutionDetailsRepository;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudPlanExecutionDetailsServiceImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock TerraformCloudPlanExecutionDetailsRepository repository;
  @InjectMocks private TerraformCloudPlanExecutionDetailsServiceImpl executionDetailsService;

  @Before
  public void setup() throws IOException {}

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testSave() {
    TerraformCloudPlanExecutionDetails terraformCloudPlanExecutionDetails =
        TerraformCloudPlanExecutionDetails.builder().build();

    executionDetailsService.save(terraformCloudPlanExecutionDetails);

    verify(repository).save(terraformCloudPlanExecutionDetails);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testDeleteAllTerraformCloudPlanExecutionDetails() {
    String pipelineExecutionId = "pipelineExecutionId";
    Scope scope = Scope.builder().build();

    executionDetailsService.deleteAllTerraformCloudPlanExecutionDetails(scope, pipelineExecutionId);

    verify(repository).deleteAllTerraformCloudPlanExecutionDetails(scope, pipelineExecutionId);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testListAllPipelineTFCloudPlanExecutionDetails() {
    String pipelineExecutionId = "pipelineExecutionId";
    Scope scope = Scope.builder().build();

    executionDetailsService.listAllPipelineTFCloudPlanExecutionDetails(scope, pipelineExecutionId);

    verify(repository).listAllPipelineTerraformCloudPlanExecutionDetails(scope, pipelineExecutionId);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testListAllPipelineTFCloudPlanExecutionDetailsException() {
    Scope scope = Scope.builder().build();

    assertThatThrownBy(() -> executionDetailsService.listAllPipelineTFCloudPlanExecutionDetails(scope, null))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Execution id cannot be null or empty");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testUpdateTerraformCloudPlanExecutionDetails() {
    String pipelineExecutionId = "pipelineExecutionId";
    String runId = "runId";
    Scope scope = Scope.builder().build();
    Map<String, Object> updates = new HashMap<>();

    executionDetailsService.updateTerraformCloudPlanExecutionDetails(scope, pipelineExecutionId, runId, updates);

    verify(repository).updateTerraformCloudPlanExecutionDetails(scope, pipelineExecutionId, runId, updates);
  }
}
