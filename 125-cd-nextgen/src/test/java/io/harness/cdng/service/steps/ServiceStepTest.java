/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

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
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.helpers.NgExpressionHelper;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceYaml;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.data.structure.UUIDGenerator;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.freeze.notifications.NotificationHelper;
import io.harness.freeze.service.FreezeEvaluateService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
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

public class ServiceStepTest extends CategoryTest {
  @Mock private ServiceEntityService serviceEntityService;
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
  @InjectMocks private ServiceStep step = new ServiceStep();

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
    final ServiceEntity serviceEntity = testServiceEntity();

    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    mockService(serviceEntity);

    StepResponse response =
        step.executeSync(buildAmbiance(), getStepParameters(serviceEntity), StepInputPackage.builder().build(), null);

    ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
    verify(freezeEvaluateService, times(1)).getActiveManualFreezeEntities(any(), any(), any(), captor.capture());

    Map<FreezeEntityType, List<String>> entityMap = captor.getValue();
    assertThat(entityMap.size()).isEqualTo(3);
    assertThat(entityMap.get(FreezeEntityType.SERVICE)).isEqualTo(Lists.newArrayList("service-id"));
    assertThat(entityMap.get(FreezeEntityType.ORG)).isEqualTo(Lists.newArrayList("ORG_ID"));
    assertThat(entityMap.get(FreezeEntityType.PROJECT)).isEqualTo(Lists.newArrayList("PROJECT_ID"));
  }

  private ServiceStepParameters getStepParameters(ServiceEntity serviceEntity) {
    return ServiceStepParameters.builder()
        .name(serviceEntity.getName())
        .identifier(serviceEntity.getIdentifier())
        .serviceRefInternal(ParameterField.createValueField(serviceEntity.getIdentifier()))
        .serviceConfigInternal(
            ParameterField.createValueField(ServiceConfig.builder()
                                                .service(ServiceYaml.builder()
                                                             .name(serviceEntity.getName())
                                                             .identifier(serviceEntity.getIdentifier())
                                                             .build())
                                                .build()))
        .build();
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void executeSyncWithFreezeOrg() {
    final ServiceEntity serviceEntity = testOrgServiceEntity();

    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    mockService(serviceEntity);

    StepResponse response =
        step.executeSync(buildAmbiance(), getStepParameters(serviceEntity), StepInputPackage.builder().build(), null);

    ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
    verify(freezeEvaluateService, times(1)).getActiveManualFreezeEntities(any(), any(), any(), captor.capture());

    Map<FreezeEntityType, List<String>> entityMap = captor.getValue();
    assertThat(entityMap.size()).isEqualTo(3);
    assertThat(entityMap.get(FreezeEntityType.SERVICE)).isEqualTo(Lists.newArrayList("org.service-id"));
    assertThat(entityMap.get(FreezeEntityType.ORG)).isEqualTo(Lists.newArrayList("ORG_ID"));
    assertThat(entityMap.get(FreezeEntityType.PROJECT)).isEqualTo(Lists.newArrayList("PROJECT_ID"));
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void executeSyncWithFreezeAccount() {
    final ServiceEntity serviceEntity = testAccountServiceEntity();

    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    mockService(serviceEntity);

    StepResponse response =
        step.executeSync(buildAmbiance(), getStepParameters(serviceEntity), StepInputPackage.builder().build(), null);

    ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
    verify(freezeEvaluateService, times(1)).getActiveManualFreezeEntities(any(), any(), any(), captor.capture());

    Map<FreezeEntityType, List<String>> entityMap = captor.getValue();
    assertThat(entityMap.size()).isEqualTo(3);
    assertThat(entityMap.get(FreezeEntityType.SERVICE)).isEqualTo(Lists.newArrayList("account.service-id"));
    assertThat(entityMap.get(FreezeEntityType.ORG)).isEqualTo(Lists.newArrayList("ORG_ID"));
    assertThat(entityMap.get(FreezeEntityType.PROJECT)).isEqualTo(Lists.newArrayList("PROJECT_ID"));
  }

  private ServiceEntity testServiceEntity() {
    final String serviceYaml = "service:\n"
        + "  name: service-name\n"
        + "  identifier: service-id\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      variables:\n"
        + "        - name: stringvar\n"
        + "          type: String\n"
        + "          value: stringvalue\n"
        + "        - name: numbervar\n"
        + "          type: Number\n"
        + "          value: 1\n"
        + "        - name: numbervar1\n"
        + "          type: Number\n"
        + "          value: 3\n"
        + "        - name: secretvar\n"
        + "          type: Secret\n"
        + "          value: org.secret\n"
        + "    type: Ssh\n";
    return ServiceEntity.builder()
        .accountId("accountId")
        .orgIdentifier("orgId")
        .projectIdentifier("projectId")
        .identifier("service-id")
        .name("service-name")
        .type(ServiceDefinitionType.KUBERNETES)
        .yaml(serviceYaml)
        .build();
  }

  private ServiceEntity testOrgServiceEntity() {
    final String serviceYaml = "service:\n"
        + "  name: service-name\n"
        + "  identifier: service-id\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      variables:\n"
        + "        - name: stringvar\n"
        + "          type: String\n"
        + "          value: stringvalue\n"
        + "    type: Ssh\n";
    return ServiceEntity.builder()
        .accountId("accountId")
        .orgIdentifier("orgId")
        .identifier("service-id")
        .name("service-name")
        .type(ServiceDefinitionType.KUBERNETES)
        .yaml(serviceYaml)
        .build();
  }

  private ServiceEntity testAccountServiceEntity() {
    final String serviceYaml = "service:\n"
        + "  name: service-name\n"
        + "  identifier: service-id\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      variables:\n"
        + "        - name: stringvar\n"
        + "          type: String\n"
        + "          value: stringvalue\n"
        + "    type: Ssh\n";
    return ServiceEntity.builder()
        .accountId("accountId")
        .identifier("service-id")
        .name("service-name")
        .type(ServiceDefinitionType.KUBERNETES)
        .yaml(serviceYaml)
        .build();
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
                         .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder()
                                               .setPrincipal("prinicipal")
                                               .setPrincipalType(io.harness.pms.contracts.plan.PrincipalType.USER)
                                               .build())
                         .build())
        .build();
  }

  private void mockService(ServiceEntity serviceEntity) {
    doReturn(Optional.ofNullable(serviceEntity))
        .when(serviceEntityService)
        .get(anyString(), anyString(), anyString(), anyString(), eq(false));
  }
}
