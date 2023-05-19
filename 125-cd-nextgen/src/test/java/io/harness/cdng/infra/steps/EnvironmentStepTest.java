/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import static io.harness.eraro.ErrorCode.FREEZE_EXCEPTION;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.category.element.UnitTests;
import io.harness.cdng.environment.yaml.EnvironmentYaml;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.helpers.NgExpressionHelper;
import io.harness.cdng.infra.InfraSectionStepParameters;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.UUIDGenerator;
import io.harness.encryption.Scope;
import io.harness.freeze.beans.CurrentOrUpcomingWindow;
import io.harness.freeze.beans.EntityConfig;
import io.harness.freeze.beans.FilterType;
import io.harness.freeze.beans.FreezeEntityRule;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.FreezeWindow;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.entity.FreezeConfigEntity.FreezeConfigEntityKeys;
import io.harness.freeze.helpers.FreezeFilterHelper;
import io.harness.freeze.helpers.FreezeTimeUtils;
import io.harness.freeze.notifications.NotificationHelper;
import io.harness.freeze.service.FrozenExecutionService;
import io.harness.freeze.service.impl.FreezeCRUDServiceImpl;
import io.harness.freeze.service.impl.FreezeEvaluateServiceImpl;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import org.mockito.Spy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.repository.support.PageableExecutionUtils;

public class EnvironmentStepTest extends CategoryTest {
  @Mock private EnvironmentService environmentService;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private CDExpressionResolver expressionResolver;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private NotificationHelper notificationHelper;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private NgExpressionHelper ngExpressionHelper;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Mock FreezeCRUDServiceImpl freezeCRUDService;
  @Mock private FrozenExecutionService frozenExecutionService;
  @Mock OutcomeService outcomeService;
  @Spy @InjectMocks private FreezeEvaluateServiceImpl freezeEvaluateService;

  private final String ACCOUNT_ID = "ACCOUNT_ID";
  private final String ORG_IDENTIFIER = "ORG_ID";
  private final String PROJ_IDENTIFIER = "PROJECT_ID";
  public DateTimeFormatter dtf = new DateTimeFormatterBuilder()
                                     .parseCaseInsensitive()
                                     .appendPattern("yyyy-MM-dd hh:mm a")
                                     .toFormatter(Locale.ENGLISH);
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
    doReturn(new ArrayList<>())
        .when(freezeEvaluateService)
        .anyGlobalFreezeActive(anyString(), anyString(), anyString());
    doReturn(new ArrayList<>())
        .when(freezeEvaluateService)
        .getActiveManualFreezeEntities(anyString(), anyString(), anyString(), any());
    doReturn(ServiceStepOutcome.builder().identifier("another-service-id").build())
        .when(outcomeService)
        .resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE)));

    StepResponse response = step.executeSyncAfterRbac(buildAmbiance(),
        InfraSectionStepParameters.builder().environmentRef(ParameterField.createValueField("envRef")).build(),
        StepInputPackage.builder().build(), null);

    ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
    verify(freezeEvaluateService, times(1)).getActiveManualFreezeEntities(any(), any(), any(), captor.capture());

    Map<FreezeEntityType, List<String>> entityMap = captor.getValue();
    assertThat(entityMap.size()).isEqualTo(6);
    assertThat(entityMap.get(FreezeEntityType.ENVIRONMENT)).isEqualTo(Lists.newArrayList("envRef"));
    assertThat(entityMap.get(FreezeEntityType.ORG)).isEqualTo(Lists.newArrayList("ORG_ID"));
    assertThat(entityMap.get(FreezeEntityType.PROJECT)).isEqualTo(Lists.newArrayList("PROJECT_ID"));
    assertThat(entityMap.get(FreezeEntityType.SERVICE)).isEqualTo(Lists.newArrayList("another-service-id"));
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void executeSyncWithFreezeOrg() {
    final Environment environment = testOrgEnvEntity();

    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    mockEnv(environment);
    doReturn(new ArrayList<>())
        .when(freezeEvaluateService)
        .anyGlobalFreezeActive(anyString(), anyString(), anyString());
    doReturn(new ArrayList<>())
        .when(freezeEvaluateService)
        .getActiveManualFreezeEntities(anyString(), anyString(), anyString(), any());
    doReturn(ServiceStepOutcome.builder().identifier("another-service-id").build())
        .when(outcomeService)
        .resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE)));

    StepResponse response = step.executeSync(buildAmbiance(),
        InfraSectionStepParameters.builder().environmentRef(ParameterField.createValueField("org.envRef")).build(),
        StepInputPackage.builder().build(), null);

    ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
    verify(freezeEvaluateService, times(1)).getActiveManualFreezeEntities(any(), any(), any(), captor.capture());

    Map<FreezeEntityType, List<String>> entityMap = captor.getValue();
    assertThat(entityMap.size()).isEqualTo(6);
    assertThat(entityMap.get(FreezeEntityType.ENVIRONMENT)).isEqualTo(Lists.newArrayList("org.envRef"));
    assertThat(entityMap.get(FreezeEntityType.ORG)).isEqualTo(Lists.newArrayList("ORG_ID"));
    assertThat(entityMap.get(FreezeEntityType.PROJECT)).isEqualTo(Lists.newArrayList("PROJECT_ID"));
    assertThat(entityMap.get(FreezeEntityType.SERVICE)).isEqualTo(Lists.newArrayList("another-service-id"));
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void executeSyncWithFreezeAccount() {
    final Environment environment = testAccountEnvEntity();

    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    mockEnv(environment);
    doReturn(new ArrayList<>())
        .when(freezeEvaluateService)
        .anyGlobalFreezeActive(anyString(), anyString(), anyString());
    doReturn(new ArrayList<>())
        .when(freezeEvaluateService)
        .getActiveManualFreezeEntities(anyString(), anyString(), anyString(), any());
    doReturn(ServiceStepOutcome.builder().identifier("another-service-id").build())
        .when(outcomeService)
        .resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE)));

    StepResponse response = step.executeSync(buildAmbiance(),
        InfraSectionStepParameters.builder().environmentRef(ParameterField.createValueField("account.envRef")).build(),
        StepInputPackage.builder().build(), null);

    ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
    verify(freezeEvaluateService, times(1)).getActiveManualFreezeEntities(any(), any(), any(), captor.capture());

    Map<FreezeEntityType, List<String>> entityMap = captor.getValue();
    assertThat(entityMap.size()).isEqualTo(6);
    assertThat(entityMap.get(FreezeEntityType.ENVIRONMENT)).isEqualTo(Lists.newArrayList("account.envRef"));
    assertThat(entityMap.get(FreezeEntityType.ORG)).isEqualTo(Lists.newArrayList("ORG_ID"));
    assertThat(entityMap.get(FreezeEntityType.PROJECT)).isEqualTo(Lists.newArrayList("PROJECT_ID"));
    assertThat(entityMap.get(FreezeEntityType.SERVICE)).isEqualTo(Lists.newArrayList("another-service-id"));
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void executeSyncWithFreezeServiceAndEnvType() {
    final Environment environment = testEnvEntity();
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    mockEnv(environment);
    initializeFreeze(true);
    doReturn(ServiceStepOutcome.builder().identifier("service-id").build())
        .when(outcomeService)
        .resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE)));
    StepResponse response = step.executeSync(buildAmbiance(),
        InfraSectionStepParameters.builder().environmentRef(ParameterField.createValueField("envRef")).build(),
        StepInputPackage.builder().build(), null);

    assertThat(response.getFailureInfo())
        .isEqualTo(FailureInfo.newBuilder()
                       .addFailureData(FailureData.newBuilder()
                                           .addFailureTypes(FailureType.FREEZE_ACTIVE_FAILURE)
                                           .setLevel(io.harness.eraro.Level.ERROR.name())
                                           .setCode(FREEZE_EXCEPTION.name())
                                           .setMessage("Pipeline Aborted due to freeze")
                                           .build())
                       .build());
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void executeSyncWithFreezeService() {
    final Environment environment = testEnvEntity();
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    mockEnv(environment);
    initializeFreeze(false);
    doReturn(ServiceStepOutcome.builder().identifier("another-service-id").build())
        .when(outcomeService)
        .resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE)));
    StepResponse response = step.executeSync(buildAmbiance(),
        InfraSectionStepParameters.builder().environmentRef(ParameterField.createValueField("envRef")).build(),
        StepInputPackage.builder().build(), null);

    assertThat(response.getFailureInfo())
        .isEqualTo(FailureInfo.newBuilder()
                       .addFailureData(FailureData.newBuilder()
                                           .addFailureTypes(FailureType.FREEZE_ACTIVE_FAILURE)
                                           .setLevel(io.harness.eraro.Level.ERROR.name())
                                           .setCode(FREEZE_EXCEPTION.name())
                                           .setMessage("Pipeline Aborted due to freeze")
                                           .build())
                       .build());
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

  private void initializeFreeze(boolean withService) {
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    if (withService) {
      entityMap.put(FreezeEntityType.SERVICE, Arrays.asList("service-id"));
    }
    entityMap.put(FreezeEntityType.ENV_TYPE, Arrays.asList("Production"));
    Criteria projectCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    Criteria orgCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, ORG_IDENTIFIER, null, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    Criteria accountCriteria = FreezeFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, null, null, null, FreezeType.MANUAL, FreezeStatus.ENABLED, null, null);
    PageRequest pageRequest = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, FreezeConfigEntityKeys.createdAt));

    FreezeSummaryResponseDTO projectLevelActiveFreezeWindow = constructActiveFreezeWindow(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "id1", Scope.PROJECT, FreezeType.MANUAL, withService);
    FreezeSummaryResponseDTO orgLevelActiveFreezeWindow = constructActiveFreezeWindow(
        ACCOUNT_ID, ORG_IDENTIFIER, null, "id2", Scope.PROJECT, FreezeType.MANUAL, withService);
    FreezeSummaryResponseDTO accountLevelActiveFreezeWindow =
        constructActiveFreezeWindow(ACCOUNT_ID, null, null, "id3", Scope.PROJECT, FreezeType.MANUAL, withService);
    Page<FreezeSummaryResponseDTO> projectLevelFreezeConfigs = PageableExecutionUtils.getPage(
        Collections.singletonList(projectLevelActiveFreezeWindow), pageRequest, () -> 1L);
    Page<FreezeSummaryResponseDTO> orgLevelFreezeConfigs =
        PageableExecutionUtils.getPage(Collections.singletonList(orgLevelActiveFreezeWindow), pageRequest, () -> 1L);
    Page<FreezeSummaryResponseDTO> accountLevelFreezeConfigs = PageableExecutionUtils.getPage(
        Collections.singletonList(accountLevelActiveFreezeWindow), pageRequest, () -> 1L);
    when(freezeCRUDService.list(projectCriteria, pageRequest)).thenReturn(projectLevelFreezeConfigs);
    when(freezeCRUDService.list(orgCriteria, pageRequest)).thenReturn(orgLevelFreezeConfigs);
    when(freezeCRUDService.list(accountCriteria, pageRequest)).thenReturn(accountLevelFreezeConfigs);
    doReturn(new ArrayList<>())
        .when(freezeEvaluateService)
        .anyGlobalFreezeActive(anyString(), anyString(), anyString());
    List<FreezeSummaryResponseDTO> activeFreezeConfigs =
        freezeEvaluateService.getActiveManualFreezeEntities(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, entityMap);
    assertThat(activeFreezeConfigs.size()).isEqualTo(3);
  }

  private FreezeSummaryResponseDTO constructActiveFreezeWindow(String accountId, String orgId, String projectId,
      String freezeId, Scope freezeScope, FreezeType freezeType, boolean withService) {
    FreezeEntityRule freezeEntityRule = new FreezeEntityRule();
    EntityConfig entityConfig = new EntityConfig();
    entityConfig.setFreezeEntityType(FreezeEntityType.ENV_TYPE);
    entityConfig.setFilterType(FilterType.EQUALS);
    entityConfig.setEntityReference(Arrays.asList("Production"));
    freezeEntityRule.setEntityConfigList(Arrays.asList(entityConfig));
    if (withService) {
      EntityConfig entityConfigService = new EntityConfig();
      entityConfigService.setFreezeEntityType(FreezeEntityType.SERVICE);
      entityConfigService.setFilterType(FilterType.EQUALS);
      entityConfigService.setEntityReference(Arrays.asList("service-id", "Service2"));
      freezeEntityRule.setEntityConfigList(Arrays.asList(entityConfig, entityConfigService));
    }
    freezeEntityRule.setName("Rule");
    FreezeWindow freezeWindow = new FreezeWindow();
    freezeWindow.setDuration("30m");
    freezeWindow.setStartTime(getCurrentTimeInString());
    freezeWindow.setTimeZone("UTC");
    CurrentOrUpcomingWindow currentOrUpcomingWindow =
        FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(Arrays.asList(freezeWindow));
    return FreezeSummaryResponseDTO.builder()
        .accountId(accountId)
        .projectIdentifier(projectId)
        .accountId(orgId)
        .identifier(freezeId)
        .freezeScope(freezeScope)
        .windows(Arrays.asList(freezeWindow))
        .status(FreezeStatus.ENABLED)
        .rules(Arrays.asList(freezeEntityRule))
        .yaml("yaml")
        .name("freeze")
        .type(freezeType)
        .currentOrUpcomingWindow(currentOrUpcomingWindow)
        .build();
  }

  private String getCurrentTimeInString() {
    LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneId.of("UTC"));
    return dtf.format(now);
  }
}
