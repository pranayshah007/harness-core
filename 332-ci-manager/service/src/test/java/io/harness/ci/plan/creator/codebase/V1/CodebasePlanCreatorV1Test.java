/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator.codebase.V1;

import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;

import com.google.cloud.ByteArray;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CI)
public class CodebasePlanCreatorV1Test extends CategoryTest {
  @Mock private KryoSerializer kryoSerializer;
  @Mock private ConnectorUtils connectorUtils;

  private String codeBaseFieldUuid;
  private String childNodeId;
  private CodeBase codeBase;
  private ExecutionSource executionSource;
  private PlanCreationContext planCreationContext;

  @Before
  public void setUp() throws Exception {
    kryoSerializer = mock(KryoSerializer.class);
    connectorUtils = mock(ConnectorUtils.class);
    codeBaseFieldUuid = UUIDGenerator.generateUuid();
    childNodeId = UUIDGenerator.generateUuid();
    executionSource = ManualExecutionSource.builder().branch("main").build();

    Build build = new Build();
    build.setSpec(BranchBuildSpec.builder().branch(ParameterField.createValueField("main")).build());
    build.setType(BuildType.BRANCH);

    codeBase = CodeBase.builder()
                   .uuid(codeBaseFieldUuid)
                   .repoName(ParameterField.createValueField("main"))
                   .connectorRef(ParameterField.createValueField("connectorRef"))
                   .build(ParameterField.createValueField(build))
                   .build();
    planCreationContext = PlanCreationContext.builder()
                              .globalContext(Collections.singletonMap("metadata",
                                  PlanCreationContextValue.newBuilder()
                                      .setAccountIdentifier("accountId")
                                      .setOrgIdentifier("orgID")
                                      .setProjectIdentifier("projectID")
                                      .build()))
                              .build();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldCreateTaskPlanForCodeBase() {
    when(kryoSerializer.asBytes(any())).thenReturn(ByteArray.copyFrom("randomBytes").toByteArray());
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .executeOnDelegate(true)
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder().apiAccess(GithubApiAccessDTO.builder().build()).build())
            .build();
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(connectorDetails);
    when(connectorUtils.hasApiAccess(connectorDetails)).thenCallRealMethod();
    PlanNode planNode = CodebasePlanCreatorV1.createPlanForCodeBase(
        planCreationContext, kryoSerializer, codeBase, connectorUtils, executionSource, childNodeId);
    assertThat(planNode.getUuid())
        .isEqualTo(codeBaseFieldUuid + "-"
            + "task");
    assertThat(planNode.getFacilitatorObtainments()).hasSize(1);
    assertThat(planNode.getFacilitatorObtainments().get(0).getType().getType())
        .isEqualTo(OrchestrationFacilitatorType.TASK);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldCreateSyncPlanForCodeBase() {
    when(kryoSerializer.asBytes(any())).thenReturn(ByteArray.copyFrom("randomBytes").toByteArray());
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .executeOnDelegate(false)
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder().apiAccess(GithubApiAccessDTO.builder().build()).build())
            .build();
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(connectorDetails);
    when(connectorUtils.hasApiAccess(connectorDetails)).thenCallRealMethod();
    PlanNode planNode = CodebasePlanCreatorV1.createPlanForCodeBase(
        planCreationContext, kryoSerializer, codeBase, connectorUtils, executionSource, childNodeId);
    assertThat(planNode.getUuid())
        .isEqualTo(codeBaseFieldUuid + "-"
            + "sync");
    assertThat(planNode.getFacilitatorObtainments()).hasSize(1);
    assertThat(planNode.getFacilitatorObtainments().get(0).getType().getType())
        .isEqualTo(OrchestrationFacilitatorType.SYNC);
  }
}
