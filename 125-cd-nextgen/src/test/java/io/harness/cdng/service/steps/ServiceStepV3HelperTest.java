/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.freeze.FreezeOutcome;
import io.harness.cdng.helpers.NgExpressionHelper;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.data.structure.UUIDGenerator;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.beans.yaml.FreezeConfig;
import io.harness.freeze.beans.yaml.FreezeInfoConfig;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.mappers.NGFreezeDtoMapper;
import io.harness.freeze.notifications.NotificationHelper;
import io.harness.freeze.service.FreezeEvaluateService;
import io.harness.freeze.service.FrozenExecutionService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class ServiceStepV3HelperTest extends CategoryTest {
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private FrozenExecutionService frozenExecutionService;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private NgExpressionHelper ngExpressionHelper;
  @Mock private NotificationHelper notificationHelper;
  @Mock private FreezeEvaluateService freezeEvaluateService;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock private AccessControlClient accessControlClient;
  private AutoCloseable mocks;

  @InjectMocks private ServiceStepV3Helper serviceStepV3Helper = new ServiceStepV3Helper();

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);

    // default behaviours
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
  @Owner(developers = OwnerRule.ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testExecuteFreezePart() {
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    List<FreezeSummaryResponseDTO> freezeSummaryResponseDTOList = Lists.newArrayList(createGlobalFreezeResponse());
    doReturn(freezeSummaryResponseDTOList)
        .when(freezeEvaluateService)
        .anyGlobalFreezeActive(anyString(), anyString(), anyString());
    when(
        accessControlClient.hasAccess(any(Principal.class), any(ResourceScope.class), any(Resource.class), anyString()))
        .thenReturn(false);
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();

    ChildrenExecutableResponse childrenExecutableResponse =
        serviceStepV3Helper.executeFreezePart(buildAmbiance(), entityMap, Collections.emptyList());
    ArgumentCaptor<ExecutionSweepingOutput> captor = ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);

    verify(sweepingOutputService, times(1))
        .consume(any(Ambiance.class), stringCaptor.capture(), captor.capture(), anyString());

    List<ExecutionSweepingOutput> allValues = captor.getAllValues();

    Map<Class, ExecutionSweepingOutput> outputsMap =
        allValues.stream().collect(Collectors.toMap(ExecutionSweepingOutput::getClass, a -> a));
    FreezeOutcome freezeOutcome = (FreezeOutcome) outputsMap.get(FreezeOutcome.class);
    assertThat(freezeOutcome.isFrozen()).isEqualTo(true);
    assertThat(freezeOutcome.getGlobalFreezeConfigs()).isEqualTo(freezeSummaryResponseDTOList);
    assertThat(childrenExecutableResponse.getChildrenCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testExecuteFreezePartWithEmptyFreezeList() {
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    List<FreezeSummaryResponseDTO> freezeSummaryResponseDTOList = Collections.emptyList();
    doReturn(freezeSummaryResponseDTOList)
        .when(freezeEvaluateService)
        .anyGlobalFreezeActive(anyString(), anyString(), anyString());
    when(
        accessControlClient.hasAccess(any(Principal.class), any(ResourceScope.class), any(Resource.class), anyString()))
        .thenReturn(false);
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();

    ChildrenExecutableResponse childrenExecutableResponse =
        serviceStepV3Helper.executeFreezePart(buildAmbiance(), entityMap, null);
    ArgumentCaptor<ExecutionSweepingOutput> captor = ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);

    verify(sweepingOutputService, times(0))
        .consume(any(Ambiance.class), stringCaptor.capture(), captor.capture(), anyString());
    assertThat(childrenExecutableResponse).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testExecuteFreezePartIfOverrideFreeze() {
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    List<FreezeSummaryResponseDTO> freezeSummaryResponseDTOList = Lists.newArrayList(createGlobalFreezeResponse());
    doReturn(freezeSummaryResponseDTOList)
        .when(freezeEvaluateService)
        .anyGlobalFreezeActive(anyString(), anyString(), anyString());
    when(
        accessControlClient.hasAccess(any(Principal.class), any(ResourceScope.class), any(Resource.class), anyString()))
        .thenReturn(true);
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();

    ChildrenExecutableResponse childrenExecutableResponse =
        serviceStepV3Helper.executeFreezePart(buildAmbiance(), entityMap, null);
    assertThat(childrenExecutableResponse).isEqualTo(null);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testExecuteFreezePartIfFreezeNotActive() {
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    doReturn(new LinkedList<>())
        .when(freezeEvaluateService)
        .anyGlobalFreezeActive(anyString(), anyString(), anyString());
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();

    serviceStepV3Helper.executeFreezePart(buildAmbiance(), entityMap, null);
    ArgumentCaptor<ExecutionSweepingOutput> captor = ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);

    verify(sweepingOutputService, times(0))
        .consume(any(Ambiance.class), stringCaptor.capture(), captor.capture(), anyString());
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
        .putAllSetupAbstractions(Map.of("accountId", "ACCOUNT_ID", "projectIdentifier", "PROJECT_ID", "orgIdentifier",
            "ORG_ID", "pipelineId", "PIPELINE_ID"))
        .addAllLevels(levels)
        .setExpressionFunctorToken(1234L)
        .setMetadata(ExecutionMetadata.newBuilder()
                         .setPipelineIdentifier("pipelineId")
                         .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder()
                                               .setPrincipal("prinicipal")
                                               .setPrincipalType(io.harness.pms.contracts.plan.PrincipalType.USER)
                                               .build())
                         .putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false)
                         .build())
        .build();
  }

  private FreezeSummaryResponseDTO createGlobalFreezeResponse() {
    FreezeConfig freezeConfig = FreezeConfig.builder()
                                    .freezeInfoConfig(FreezeInfoConfig.builder()
                                                          .identifier("_GLOBAL_")
                                                          .name("Global Freeze")
                                                          .status(FreezeStatus.DISABLED)
                                                          .build())
                                    .build();
    String yaml = NGFreezeDtoMapper.toYaml(freezeConfig);
    FreezeConfigEntity freezeConfigEntity = NGFreezeDtoMapper.toFreezeConfigEntity(
        "accountId", "orgIdentifier", "projectIdentifier", yaml, FreezeType.GLOBAL);
    return NGFreezeDtoMapper.prepareFreezeResponseSummaryDto(freezeConfigEntity);
  }
}
