/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator.codebase;

import static io.harness.rule.OwnerRule.ALEKSANDAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;

import com.google.cloud.ByteArray;
import java.util.List;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CI)
public class CodebasePlanCreatorTest extends CategoryTest {
  @Mock private KryoSerializer kryoSerializer;

  private String codeBaseFieldUuid;
  private String childNodeId;
  private CodeBase codeBase;
  private ExecutionSource executionSource;

  @Before
  public void setUp() throws Exception {
    kryoSerializer = mock(KryoSerializer.class);
    codeBaseFieldUuid = UUIDGenerator.generateUuid();
    childNodeId = UUIDGenerator.generateUuid();
    executionSource = ManualExecutionSource.builder().branch("main").build();

    Build build = new Build();
    build.setSpec(BranchBuildSpec.builder().branch(ParameterField.createValueField("main")).build());
    build.setType(BuildType.BRANCH);

    codeBase = CodeBase.builder()
                   .repoName(ParameterField.createValueField("main"))
                   .connectorRef(ParameterField.createValueField("connectorRef"))
                   .build(ParameterField.createValueField(build))
                   .build();
  }
  @After
  public void tearDown() throws Exception {}

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldCreatePlanForCodeBase() {
    when(kryoSerializer.asBytes(any())).thenReturn(ByteArray.copyFrom("randomBytes").toByteArray());
    List<PlanNode> planNodeList = CodebasePlanCreator.buildCodebasePlanNodes(
        codeBaseFieldUuid, childNodeId, kryoSerializer, codeBase, executionSource);
    assertThat(planNodeList).hasSize(3);
    assertThat(planNodeList).allMatch(planNode -> planNode.getUuid().contains(codeBaseFieldUuid));
    Optional<PlanNode> codeBasePlanNode =
        planNodeList.stream().filter(planNode -> planNode.getUuid().equals(codeBaseFieldUuid)).findFirst();
    assertThat(codeBasePlanNode.isPresent()).isTrue();
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void createPlanForCodeBaseTask() {
    PlanNode sync = CodebasePlanCreator.createPlanForCodeBaseTask(codeBase, executionSource, "SYNC", codeBaseFieldUuid);
    assertThat(sync.getFacilitatorObtainments())
        .containsExactly(
            FacilitatorObtainment.newBuilder().setType(FacilitatorType.newBuilder().setType("SYNC").build()).build());

    PlanNode task = CodebasePlanCreator.createPlanForCodeBaseTask(codeBase, executionSource, "TASK", codeBaseFieldUuid);
    assertThat(task.getFacilitatorObtainments())
        .containsExactly(
            FacilitatorObtainment.newBuilder().setType(FacilitatorType.newBuilder().setType("TASK").build()).build());
  }
}
