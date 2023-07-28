/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.executables;

import static io.harness.rule.OwnerRule.YOGESH;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.execution.service.StageExecutionInstanceInfoService;
import io.harness.delegate.beans.CDDelegateTaskNotifyResponseData;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.cdng.execution.StepExecutionInstanceInfo;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CdTaskChainExecutableTest extends CategoryTest {
  private final Ambiance ambiance = buildAmbiance();
  @Mock private StageExecutionInstanceInfoService stageExecutionInstanceInfoService;
  @InjectMocks private final CdTaskChainExecutable target = new TestExecutable();
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
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void executeNextLinkWithSecurityContext() throws Exception {
    target.executeNextLinkWithSecurityContext(
        ambiance, StepElementParameters.builder().build(), StepInputPackage.builder().build(), null, null);

    target.executeNextLinkWithSecurityContext(ambiance, StepElementParameters.builder().build(),
        StepInputPackage.builder().build(), null, TestResponseData::new);

    target.executeNextLinkWithSecurityContext(ambiance, StepElementParameters.builder().build(),
        StepInputPackage.builder().build(), null, () -> { throw new RuntimeException("something went wrong!"); });

    verify(stageExecutionInstanceInfoService)
        .append(anyString(), anyString(), anyString(), anyString(), anyString(),
            eq(StepExecutionInstanceInfo.builder().build()));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContext() throws Exception {
    target.finalizeExecutionWithSecurityContext(ambiance, StepElementParameters.builder().build(), null, null);

    target.finalizeExecutionWithSecurityContext(
        ambiance, StepElementParameters.builder().build(), null, TestResponseData::new);

    target.finalizeExecutionWithSecurityContext(ambiance, StepElementParameters.builder().build(), null,
        () -> { throw new RuntimeException("something went wrong!"); });

    verify(stageExecutionInstanceInfoService)
        .append(anyString(), anyString(), anyString(), anyString(), anyString(),
            eq(StepExecutionInstanceInfo.builder().build()));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextAndNodeInfo() {}

  private Ambiance buildAmbiance() {
    Level phaseLevel =
        Level.newBuilder()
            .setRuntimeId("PHASE_RUNTIME_ID")
            .setSetupId("PHASE_SETUP_ID")
            .setStepType(StepType.newBuilder().setType("DEPLOY_PHASE").setStepCategory(StepCategory.STEP).build())
            .setGroup("PHASE")
            .build();
    Level sectionLevel =
        Level.newBuilder()
            .setRuntimeId("SECTION_RUNTIME_ID")
            .setSetupId("SECTION_SETUP_ID")
            .setStepType(StepType.newBuilder().setType("DEPLOY_SECTION").setStepCategory(StepCategory.STEP).build())
            .setGroup("SECTION")
            .build();
    List<Level> levels = new ArrayList<>();
    levels.add(phaseLevel);
    levels.add(sectionLevel);
    return Ambiance.newBuilder()
        .setPlanExecutionId("EXECUTION_INSTANCE_ID")
        .putAllSetupAbstractions(ImmutableMap.of(
            "accountId", "ACCOUNT_ID", "appId", "APP_ID", "orgIdentifier", "ORG_ID", "projectIdentifier", "PROJECT_ID"))
        .addAllLevels(levels)
        .build();
  }

  // classes for testing
  class TestExecutable extends CdTaskChainExecutable {
    @Override
    public TaskChainResponse executeNextLinkWithSecurityContextAndNodeInfo(Ambiance ambiance,
        StepElementParameters stepParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
        ThrowingSupplier<ResponseData> responseSupplier) {
      return null;
    }

    @Override
    public StepResponse finalizeExecutionWithSecurityContextAndNodeInfo(Ambiance ambiance,
        StepElementParameters stepParameters, PassThroughData passThroughData,
        ThrowingSupplier<ResponseData> responseDataSupplier) {
      return null;
    }

    @Override
    public Class<StepElementParameters> getStepParametersClass() {
      return null;
    }

    @Override
    public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {}

    @Override
    public TaskChainResponse startChainLinkAfterRbac(
        Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
      return null;
    }
  }

  @AllArgsConstructor
  class TestResponseData implements CDDelegateTaskNotifyResponseData {
    @Override
    public StepExecutionInstanceInfo getStepExecutionInstanceInfo() {
      return StepExecutionInstanceInfo.builder().build();
    }

    @Override
    public DelegateMetaInfo getDelegateMetaInfo() {
      return DelegateMetaInfo.builder().build();
    }

    @Override
    public void setDelegateMetaInfo(DelegateMetaInfo metaInfo) {}
  }
}
