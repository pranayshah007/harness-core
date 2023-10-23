/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.ngexception.NGFreezeException;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.beans.yaml.FreezeConfig;
import io.harness.freeze.beans.yaml.FreezeInfoConfig;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.mappers.NGFreezeDtoMapper;
import io.harness.freeze.service.FreezeEvaluateService;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.PlanExecutionContext;
import io.harness.pms.contracts.plan.PrincipalType;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
public class StagePlanCreatorHelperTest extends CategoryTest {
  @Mock private AccessControlClient accessControlClient;
  @Mock private FreezeEvaluateService freezeEvaluateService;
  @Mock private NGFeatureFlagHelperService featureFlagHelperService;
  @InjectMocks private StagePlanCreatorHelper stagePlanCreatorHelper;

  private AutoCloseable mocks;
  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
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
  public void failIfProjectIsFrozen() {
    List<FreezeSummaryResponseDTO> freezeSummaryResponseDTOList = Lists.newArrayList(createGlobalFreezeResponse());
    doReturn(freezeSummaryResponseDTOList)
        .when(freezeEvaluateService)
        .getActiveFreezeEntities(anyString(), anyString(), anyString(), anyString());
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Map.of("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setAccountIdentifier("accountId")
                                          .setOrgIdentifier("orgId")
                                          .setProjectIdentifier("projId")
                                          .setExecutionContext(PlanExecutionContext.newBuilder().setPrincipalInfo(
                                              ExecutionPrincipalInfo.newBuilder()
                                                  .setPrincipal("prinicipal")
                                                  .setPrincipalType(PrincipalType.USER)
                                                  .build()))
                                          .build()))
                                  .build();
    when(accessControlClient.hasAccess(any(ResourceScope.class), any(Resource.class), anyString())).thenReturn(false);
    assertThatThrownBy(() -> stagePlanCreatorHelper.failIfProjectIsFrozen(ctx))
        .isInstanceOf(NGFreezeException.class)
        .matches(ex -> ex.getMessage().equals("Execution can't be performed because project is frozen"));

    verify(freezeEvaluateService, times(1)).getActiveFreezeEntities(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void failIfProjectIsFrozenWithOverridePermission() {
    doReturn(false).when(featureFlagHelperService).isEnabled(anyString(), any());
    List<FreezeSummaryResponseDTO> freezeSummaryResponseDTOList = Lists.newArrayList(createGlobalFreezeResponse());
    doReturn(freezeSummaryResponseDTOList)
        .when(freezeEvaluateService)
        .getActiveFreezeEntities(anyString(), anyString(), anyString(), anyString());
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Map.of("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setAccountIdentifier("accountId")
                                          .setOrgIdentifier("orgId")
                                          .setProjectIdentifier("projId")
                                          .setExecutionContext(PlanExecutionContext.newBuilder().setPrincipalInfo(
                                              ExecutionPrincipalInfo.newBuilder()
                                                  .setPrincipal("prinicipal")
                                                  .setPrincipalType(io.harness.pms.contracts.plan.PrincipalType.USER)
                                                  .build()))
                                          .build()))
                                  .build();
    when(
        accessControlClient.hasAccess(any(Principal.class), any(ResourceScope.class), any(Resource.class), anyString()))
        .thenReturn(true);
    stagePlanCreatorHelper.failIfProjectIsFrozen(ctx);

    verify(freezeEvaluateService, times(0)).getActiveFreezeEntities(anyString(), anyString(), anyString(), anyString());
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
    FreezeConfigEntity freezeConfigEntity =
        NGFreezeDtoMapper.toFreezeConfigEntity("accountId", "orgId", "projId", yaml, FreezeType.GLOBAL);
    return NGFreezeDtoMapper.prepareFreezeResponseSummaryDto(freezeConfigEntity);
  }
}
