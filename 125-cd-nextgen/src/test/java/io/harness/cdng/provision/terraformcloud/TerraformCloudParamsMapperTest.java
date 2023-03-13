/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud;

import static io.harness.rule.OwnerRule.BUHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudApplySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanAndApplySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanAndDestroySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanOnlySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanSpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudRefreshSpecParameters;
import io.harness.delegate.task.terraformcloud.TerraformCloudTaskType;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudApplyTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudPlanAndApplyTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudPlanAndDestroyTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudPlanOnlyTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudPlanTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudRefreshTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudTaskParams;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudParamsMapperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private TerraformCloudStepHelper helper;
  @InjectMocks TerraformCloudParamsMapper mapper;

  private TerraformCloudTestStepUtils utils;
  private Ambiance ambiance;

  @Before
  public void setup() throws IOException {
    utils = new TerraformCloudTestStepUtils();
    ambiance = utils.getAmbiance();
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testMapRefreshStateParams() {
    TerraformCloudRefreshSpecParameters specParams = utils.createRefreshSpecParams();
    specParams.setDiscardPendingRuns(ParameterField.createValueField(true));

    TerraformCloudTaskParams terraformCloudTaskParams =
        mapper.mapRunSpecToTaskParams(TerraformCloudRunStepParameters.infoBuilder().spec(specParams).build(), ambiance);

    assertThat(terraformCloudTaskParams.getTaskType()).isEqualTo(TerraformCloudTaskType.RUN_REFRESH_STATE);
    TerraformCloudRefreshTaskParams refreshTaskParams = (TerraformCloudRefreshTaskParams) terraformCloudTaskParams;
    assertThat(refreshTaskParams.getWorkspace()).isEqualTo("ws");
    assertThat(refreshTaskParams.isDiscardPendingRuns()).isTrue();
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testMapPlanOnlyParams() {
    TerraformCloudPlanOnlySpecParameters specParams = utils.createPlanOnly();
    specParams.setDiscardPendingRuns(ParameterField.createValueField(true));

    TerraformCloudTaskParams terraformCloudTaskParams =
        mapper.mapRunSpecToTaskParams(TerraformCloudRunStepParameters.infoBuilder().spec(specParams).build(), ambiance);

    assertThat(terraformCloudTaskParams.getTaskType()).isEqualTo(TerraformCloudTaskType.RUN_PLAN_ONLY);
    TerraformCloudPlanOnlyTaskParams terraformCloudPlanOnlyTaskParams =
        (TerraformCloudPlanOnlyTaskParams) terraformCloudTaskParams;
    assertThat(terraformCloudPlanOnlyTaskParams.getWorkspace()).isEqualTo("ws");
    assertThat(terraformCloudPlanOnlyTaskParams.isDiscardPendingRuns()).isTrue();
    assertThat(terraformCloudPlanOnlyTaskParams.isExportJsonTfPlan()).isTrue();
    assertThat(terraformCloudPlanOnlyTaskParams.getPlanType().name()).isEqualTo("APPLY");
    assertThat(terraformCloudPlanOnlyTaskParams.getTerraformVersion()).isEqualTo("123");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testMapPlanAndApplyParams() {
    TerraformCloudPlanAndApplySpecParameters specParams = utils.createPlanAndApply();
    specParams.setDiscardPendingRuns(ParameterField.createValueField(true));
    TerraformCloudTaskParams terraformCloudTaskParams =
        mapper.mapRunSpecToTaskParams(TerraformCloudRunStepParameters.infoBuilder().spec(specParams).build(), ambiance);

    assertThat(terraformCloudTaskParams.getTaskType()).isEqualTo(TerraformCloudTaskType.RUN_PLAN_AND_APPLY);
    TerraformCloudPlanAndApplyTaskParams terraformCloudPlanAndApplyTaskParams =
        (TerraformCloudPlanAndApplyTaskParams) terraformCloudTaskParams;
    assertThat(terraformCloudPlanAndApplyTaskParams.getWorkspace()).isEqualTo("ws");
    assertThat(terraformCloudPlanAndApplyTaskParams.isDiscardPendingRuns()).isTrue();
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testMapPlanAndDestroyParams() {
    TerraformCloudPlanAndDestroySpecParameters specParams = utils.createPlanAndDestroy();
    specParams.setDiscardPendingRuns(ParameterField.createValueField(true));

    TerraformCloudTaskParams terraformCloudTaskParams =
        mapper.mapRunSpecToTaskParams(TerraformCloudRunStepParameters.infoBuilder().spec(specParams).build(), ambiance);

    assertThat(terraformCloudTaskParams.getTaskType()).isEqualTo(TerraformCloudTaskType.RUN_PLAN_AND_DESTROY);
    TerraformCloudPlanAndDestroyTaskParams terraformCloudPlanAndDestroyTaskParams =
        (TerraformCloudPlanAndDestroyTaskParams) terraformCloudTaskParams;
    assertThat(terraformCloudPlanAndDestroyTaskParams.getWorkspace()).isEqualTo("ws");
    assertThat(terraformCloudPlanAndDestroyTaskParams.isDiscardPendingRuns()).isTrue();
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testMapPlanParams() {
    TerraformCloudPlanSpecParameters specParams = utils.getPlanSpecParameters();
    specParams.setDiscardPendingRuns(ParameterField.createValueField(true));

    TerraformCloudTaskParams terraformCloudTaskParams =
        mapper.mapRunSpecToTaskParams(TerraformCloudRunStepParameters.infoBuilder().spec(specParams).build(), ambiance);

    assertThat(terraformCloudTaskParams.getTaskType()).isEqualTo(TerraformCloudTaskType.RUN_PLAN);
    TerraformCloudPlanTaskParams terraformCloudPlanTaskParams = (TerraformCloudPlanTaskParams) terraformCloudTaskParams;
    assertThat(terraformCloudPlanTaskParams.getWorkspace()).isEqualTo("ws");
    assertThat(terraformCloudPlanTaskParams.isDiscardPendingRuns()).isTrue();
    assertThat(terraformCloudPlanTaskParams.isExportJsonTfPlan()).isTrue();
    assertThat(terraformCloudPlanTaskParams.getPlanType().name()).isEqualTo("APPLY");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testMapApplyParams() {
    TerraformCloudApplySpecParameters specParams = utils.getApplySpecParameters();
    doReturn("run-123").when(helper).getPlanRunId("provisionerId", ambiance);

    TerraformCloudTaskParams terraformCloudTaskParams =
        mapper.mapRunSpecToTaskParams(TerraformCloudRunStepParameters.infoBuilder().spec(specParams).build(), ambiance);

    assertThat(terraformCloudTaskParams.getTaskType()).isEqualTo(TerraformCloudTaskType.RUN_APPLY);
    TerraformCloudApplyTaskParams terraformCloudApplyTaskParams =
        (TerraformCloudApplyTaskParams) terraformCloudTaskParams;
    assertThat(terraformCloudApplyTaskParams.getRunId()).isEqualTo("run-123");
  }
}
