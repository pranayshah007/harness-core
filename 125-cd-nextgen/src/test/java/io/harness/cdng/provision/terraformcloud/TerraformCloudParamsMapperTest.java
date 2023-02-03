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
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskParams;
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskType;
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
    TerraformCloudTaskParams terraformCloudTaskParams = mapper.mapRunSpecToTaskParams(specParams, ambiance);

    assertThat(terraformCloudTaskParams.getTerraformCloudTaskType())
        .isEqualTo(TerraformCloudTaskType.RUN_REFRESH_STATE);
    assertThat(terraformCloudTaskParams.getOrganization()).isEqualTo("org");
    assertThat(terraformCloudTaskParams.getWorkspace()).isEqualTo("ws");
    assertThat(terraformCloudTaskParams.isDiscardPendingRuns()).isTrue();
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testMapPlanOnlyParams() {
    TerraformCloudPlanOnlySpecParameters specParams = utils.createPlanOnly();
    specParams.setDiscardPendingRuns(ParameterField.createValueField(true));
    TerraformCloudTaskParams terraformCloudTaskParams = mapper.mapRunSpecToTaskParams(specParams, ambiance);

    assertThat(terraformCloudTaskParams.getTerraformCloudTaskType()).isEqualTo(TerraformCloudTaskType.RUN_PLAN_ONLY);
    assertThat(terraformCloudTaskParams.getOrganization()).isEqualTo("org");
    assertThat(terraformCloudTaskParams.getWorkspace()).isEqualTo("ws");
    assertThat(terraformCloudTaskParams.isDiscardPendingRuns()).isTrue();
    assertThat(terraformCloudTaskParams.isExportJsonTfPlan()).isTrue();
    assertThat(terraformCloudTaskParams.getPlanType().name()).isEqualTo("APPLY");
    assertThat(terraformCloudTaskParams.getTerraformVersion()).isEqualTo("123");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testMapPlanAndApplyParams() {
    TerraformCloudPlanAndApplySpecParameters specParams = utils.createPlanAndApply();
    specParams.setDiscardPendingRuns(ParameterField.createValueField(true));
    TerraformCloudTaskParams terraformCloudTaskParams = mapper.mapRunSpecToTaskParams(specParams, ambiance);

    assertThat(terraformCloudTaskParams.getTerraformCloudTaskType())
        .isEqualTo(TerraformCloudTaskType.RUN_PLAN_AND_APPLY);
    assertThat(terraformCloudTaskParams.getOrganization()).isEqualTo("org");
    assertThat(terraformCloudTaskParams.getWorkspace()).isEqualTo("ws");
    assertThat(terraformCloudTaskParams.isDiscardPendingRuns()).isTrue();
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testMapPlanAndDestroyParams() {
    TerraformCloudPlanAndDestroySpecParameters specParams = utils.createPlanAndDestroy();
    specParams.setDiscardPendingRuns(ParameterField.createValueField(true));
    TerraformCloudTaskParams terraformCloudTaskParams = mapper.mapRunSpecToTaskParams(specParams, ambiance);

    assertThat(terraformCloudTaskParams.getTerraformCloudTaskType())
        .isEqualTo(TerraformCloudTaskType.RUN_PLAN_AND_DESTROY);
    assertThat(terraformCloudTaskParams.getOrganization()).isEqualTo("org");
    assertThat(terraformCloudTaskParams.getWorkspace()).isEqualTo("ws");
    assertThat(terraformCloudTaskParams.isDiscardPendingRuns()).isTrue();
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testMapPlanParams() {
    TerraformCloudPlanSpecParameters specParams = utils.getPlanSpecParameters();
    specParams.setDiscardPendingRuns(ParameterField.createValueField(true));
    TerraformCloudTaskParams terraformCloudTaskParams = mapper.mapRunSpecToTaskParams(specParams, ambiance);

    assertThat(terraformCloudTaskParams.getTerraformCloudTaskType()).isEqualTo(TerraformCloudTaskType.RUN_PLAN);
    assertThat(terraformCloudTaskParams.getOrganization()).isEqualTo("org");
    assertThat(terraformCloudTaskParams.getWorkspace()).isEqualTo("ws");
    assertThat(terraformCloudTaskParams.isDiscardPendingRuns()).isTrue();
    assertThat(terraformCloudTaskParams.isExportJsonTfPlan()).isTrue();
    assertThat(terraformCloudTaskParams.getPlanType().name()).isEqualTo("APPLY");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testMapApplyParams() {
    TerraformCloudApplySpecParameters specParams = utils.getApplySpecParameters();
    doReturn("run-123").when(helper).getPlanRunId("provisionerId", ambiance);

    TerraformCloudTaskParams terraformCloudTaskParams = mapper.mapRunSpecToTaskParams(specParams, ambiance);

    assertThat(terraformCloudTaskParams.getTerraformCloudTaskType()).isEqualTo(TerraformCloudTaskType.RUN_APPLY);
    assertThat(terraformCloudTaskParams.getRunId()).isEqualTo("run-123");
  }
}
