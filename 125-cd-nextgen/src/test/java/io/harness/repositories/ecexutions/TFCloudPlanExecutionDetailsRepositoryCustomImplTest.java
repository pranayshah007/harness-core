/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.ecexutions;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.cdng.provision.terraform.executions.TerraformCloudPlanExecutionDetails;
import io.harness.repositories.executions.TFCloudPlanExecutionDetailsRepositoryCustomImpl;
import io.harness.rule.Owner;

import java.util.HashMap;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.springframework.data.mongodb.core.MongoTemplate;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class TFCloudPlanExecutionDetailsRepositoryCustomImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private MongoTemplate mongoTemplate;
  @InjectMocks private TFCloudPlanExecutionDetailsRepositoryCustomImpl repository;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Reflect.on(repository).set("mongoTemplate", mongoTemplate);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testDeleteAllTerraformPlanExecutionDetails() {
    Scope scope = mock(Scope.class);

    repository.deleteAllTerraformCloudPlanExecutionDetails(scope, "pipelineExecutionId");

    verify(mongoTemplate).remove(any(), eq(TerraformCloudPlanExecutionDetails.class));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testListAllPipelineTFPlanExecutionDetails() {
    Scope scope = mock(Scope.class);

    repository.listAllPipelineTerraformCloudPlanExecutionDetails(scope, "pipelineExecutionId");

    verify(mongoTemplate).find(any(), eq(TerraformCloudPlanExecutionDetails.class));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testUpdateTerraformCloudPlanExecutionDetails() {
    Scope scope = mock(Scope.class);

    repository.updateTerraformCloudPlanExecutionDetails(scope, "pipelineExecutionId", "runId", new HashMap<>());

    verify(mongoTemplate).findAndModify(any(), any(), eq(TerraformCloudPlanExecutionDetails.class));
  }
}
