/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.executables;

import static io.harness.rule.OwnerRule.BUHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.rule.Owner;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

public class CdAsyncExecutableTest extends CategoryTest {
  @Mock private CdTaskExecutableTest cdTaskExecutable;
  @InjectMocks private CdAsyncExecutable<CdTaskExecutableTest> cdAsyncExecutable;

  Ambiance ambiance = buildAmbiance();
  StepElementParameters stepElementParameters = StepElementParameters.builder().build();
  private AutoCloseable mocks;

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    ReflectionTestUtils.setField(cdAsyncExecutable, "cdTaskExecutable", cdTaskExecutable);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    when(cdTaskExecutable.getStepParametersClass()).thenReturn(StepBaseParameters.class);

    Class<StepBaseParameters> stepParametersClass = cdAsyncExecutable.getStepParametersClass();

    verify(cdTaskExecutable).getStepParametersClass();
    assertThat(stepParametersClass).isEqualTo(StepBaseParameters.class);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testValidateResources() {
    cdAsyncExecutable.validateResources(ambiance, stepElementParameters);
    verify(cdTaskExecutable).validateResources(ambiance, stepElementParameters);
  }

  static class CdTaskExecutableTest extends CdTaskExecutable<ResponseData> {
    @Override
    public Class<StepBaseParameters> getStepParametersClass() {
      return null;
    }

    @Override
    public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(
        Ambiance ambiance, StepBaseParameters stepParameters, ThrowingSupplier responseDataSupplier) throws Exception {
      return null;
    }

    @Override
    public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {}

    @Override
    public TaskRequest obtainTaskAfterRbac(
        Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
      return null;
    }
  }

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
}
