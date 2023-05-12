/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.category.element.UnitTests;
import io.harness.cdng.environment.yaml.EnvironmentYaml;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.helpers.NgExpressionHelper;
import io.harness.cdng.infra.InfraSectionStepParameters;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.data.structure.UUIDGenerator;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.freeze.notifications.NotificationHelper;
import io.harness.freeze.service.FreezeEvaluateService;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EnvironmentStepTest extends CategoryTest {
  @Mock private EnvironmentService environmentService;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private CDExpressionResolver expressionResolver;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock private FreezeEvaluateService freezeEvaluateService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private NotificationHelper notificationHelper;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private NgExpressionHelper ngExpressionHelper;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private EntityReferenceExtractorUtils entityReferenceExtractorUtils;

  private AutoCloseable mocks;
  @InjectMocks private EnvironmentStep step = new EnvironmentStep();

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);

    doReturn(false).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());

    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class), any());

    doReturn(AccessCheckResponseDTO.builder().accessControlList(List.of()).build())
        .when(accessControlClient)
        .checkForAccess(any(Principal.class), anyList());
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void executeSyncWithFreezeProject() {
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    final Environment environment = testEnvEntity();
    mockEnv(environment);

    StepResponse response = step.executeSyncAfterRbac(buildAmbiance(),
        InfraSectionStepParameters.builder().environmentRef(ParameterField.createValueField("envRef")).build(),
        StepInputPackage.builder().build(), null);

    ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
    verify(freezeEvaluateService, times(1)).getActiveManualFreezeEntities(any(), any(), any(), captor.capture());

    Map<FreezeEntityType, List<String>> entityMap = captor.getValue();
    assertThat(entityMap.size()).isEqualTo(5);
    assertThat(entityMap.get(FreezeEntityType.ENVIRONMENT)).isEqualTo(Lists.newArrayList("envRef"));
    assertThat(entityMap.get(FreezeEntityType.ORG)).isEqualTo(Lists.newArrayList("ORG_ID"));
    assertThat(entityMap.get(FreezeEntityType.PROJECT)).isEqualTo(Lists.newArrayList("PROJECT_ID"));
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void executeSyncWithFreezeOrg() {
    final Environment environment = testOrgEnvEntity();

    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    mockEnv(environment);

    StepResponse response = step.executeSync(buildAmbiance(),
        InfraSectionStepParameters.builder().environmentRef(ParameterField.createValueField("org.envRef")).build(),
        StepInputPackage.builder().build(), null);

    ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
    verify(freezeEvaluateService, times(1)).getActiveManualFreezeEntities(any(), any(), any(), captor.capture());

    Map<FreezeEntityType, List<String>> entityMap = captor.getValue();
    assertThat(entityMap.size()).isEqualTo(5);
    assertThat(entityMap.get(FreezeEntityType.ENVIRONMENT)).isEqualTo(Lists.newArrayList("org.envRef"));
    assertThat(entityMap.get(FreezeEntityType.ORG)).isEqualTo(Lists.newArrayList("ORG_ID"));
    assertThat(entityMap.get(FreezeEntityType.PROJECT)).isEqualTo(Lists.newArrayList("PROJECT_ID"));
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void executeSyncWithFreezeAccount() {
    final Environment environment = testAccountEnvEntity();

    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    mockEnv(environment);

    StepResponse response = step.executeSync(buildAmbiance(),
        InfraSectionStepParameters.builder().environmentRef(ParameterField.createValueField("account.envRef")).build(),
        StepInputPackage.builder().build(), null);

    ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
    verify(freezeEvaluateService, times(1)).getActiveManualFreezeEntities(any(), any(), any(), captor.capture());

    Map<FreezeEntityType, List<String>> entityMap = captor.getValue();
    assertThat(entityMap.size()).isEqualTo(5);
    assertThat(entityMap.get(FreezeEntityType.ENVIRONMENT)).isEqualTo(Lists.newArrayList("account.envRef"));
    assertThat(entityMap.get(FreezeEntityType.ORG)).isEqualTo(Lists.newArrayList("ORG_ID"));
    assertThat(entityMap.get(FreezeEntityType.PROJECT)).isEqualTo(Lists.newArrayList("PROJECT_ID"));
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void testGetEnviromentRef() {
    assertThat(step.getEnviromentRef(testEnvYaml(), null, null)).isEqualTo("envId");
    assertThat(step.getEnviromentRef(testEnvYaml(), ParameterField.createValueField("envRef"), null))
        .isEqualTo("envRef");
    assertThat(step.getEnviromentRef(testEnvYaml(), ParameterField.createValueField("org.envRef"), null))
        .isEqualTo("org.envRef");
    assertThat(step.getEnviromentRef(testEnvYaml(), ParameterField.createValueField("account.envRef"), null))
        .isEqualTo("account.envRef");
    assertThat(step.getEnviromentRef(null, null, EnvironmentOutcome.builder().identifier("envId").build()))
        .isEqualTo("envId");
  }

  private Environment testEnvEntity() {
    String yaml = "environment:\n"
        + "  name: developmentEnv\n"
        + "  identifier: envId\n"
        + "  type: Production\n"
        + "  orgIdentifier: orgId\n"
        + "  projectIdentifier: projectId\n"
        + "  variables:\n"
        + "    - name: stringvar\n"
        + "      type: String\n"
        + "      value: envvalue\n"
        + "    - name: numbervar\n"
        + "      type: Number\n"
        + "      value: 5";
    return Environment.builder()
        .accountId("accountId")
        .orgIdentifier("orgId")
        .projectIdentifier("projectId")
        .identifier("envId")
        .name("developmentEnv")
        .type(EnvironmentType.Production)
        .yaml(yaml)
        .build();
  }

  private Environment testOrgEnvEntity() {
    String yaml = "environment:\n"
        + "  name: developmentEnv\n"
        + "  identifier: envId\n"
        + "  type: Production\n"
        + "  orgIdentifier: orgId\n"
        + "  variables:\n"
        + "    - name: stringvar\n"
        + "      type: String\n"
        + "      value: envvalue\n"
        + "    - name: numbervar\n"
        + "      type: Number\n"
        + "      value: 5";
    return Environment.builder()
        .accountId("accountId")
        .orgIdentifier("orgId")
        .identifier("envId")
        .name("developmentEnv")
        .type(EnvironmentType.Production)
        .yaml(yaml)
        .build();
  }

  private Environment testAccountEnvEntity() {
    String yaml = "environment:\n"
        + "  name: developmentEnv\n"
        + "  identifier: envId\n"
        + "  type: Production\n"
        + "  variables:\n"
        + "    - name: stringvar\n"
        + "      type: String\n"
        + "      value: envvalue\n"
        + "    - name: numbervar\n"
        + "      type: Number\n"
        + "      value: 5";
    return Environment.builder()
        .accountId("accountId")
        .identifier("envId")
        .name("developmentEnv")
        .type(EnvironmentType.Production)
        .yaml(yaml)
        .build();
  }

  private EnvironmentYaml testEnvYaml() {
    return EnvironmentYaml.builder()
        .identifier("envId")
        .name("developmentEnv")
        .type(EnvironmentType.Production)
        .build();
  }

  private void mockEnv(Environment environment) {
    doReturn(Optional.ofNullable(environment))
        .when(environmentService)
        .get(anyString(), anyString(), anyString(), anyString(), eq(false));
  }

  private Ambiance buildAmbiance() {
    List<Level> levels = new ArrayList();
    levels.add(Level.newBuilder()
                   .setRuntimeId(UUIDGenerator.generateUuid())
                   .setSetupId(UUIDGenerator.generateUuid())
                   .setStepType(ServiceStepV3Constants.STEP_TYPE)
                   .build());
    return Ambiance.newBuilder()
        .setPlanExecutionId(UUIDGenerator.generateUuid())
        .putAllSetupAbstractions(
            Map.of("accountId", "ACCOUNT_ID", "projectIdentifier", "PROJECT_ID", "orgIdentifier", "ORG_ID"))
        .addAllLevels(levels)
        .setExpressionFunctorToken(1234L)
        .setMetadata(ExecutionMetadata.newBuilder()
                         .setPipelineIdentifier("pipelineId")
                         .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder()
                                               .setPrincipal("prinicipal")
                                               .setPrincipalType(io.harness.pms.contracts.plan.PrincipalType.USER)
                                               .build())
                         .build())
        .build();
  }
}
