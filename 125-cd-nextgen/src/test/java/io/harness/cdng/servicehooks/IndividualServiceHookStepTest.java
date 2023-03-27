/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.servicehooks;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.FAILED_CHILDREN_OUTPUT;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.configfile.ConfigFileOutcome;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.hooks.ServiceHookAction;
import io.harness.cdng.hooks.ServiceHookOutcome;
import io.harness.cdng.hooks.ServiceHookType;
import io.harness.cdng.hooks.steps.IndividualServiceHookStep;
import io.harness.cdng.hooks.steps.ServiceHookStepParameters;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class IndividualServiceHookStepTest extends CDNGTestBase {
  private static final String IDENTIFIER = "identifier";
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";

  @Mock private ServiceStepsHelper serviceStepsHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;

  @Mock private CDExpressionResolver cdExpressionResolver;

  @InjectMocks private IndividualServiceHookStep individualServiceHookStep;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(individualServiceHookStep.getStepParametersClass()).isEqualTo(ServiceHookStepParameters.class);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testExecuteSyncInlineStore() {
    Ambiance ambiance = getAmbiance();
    Map<String, ResponseData> responseData = new HashMap<>();
    when(serviceStepsHelper.getChildrenOutcomes(responseData))
        .thenReturn(Collections.singletonList(ConfigFileOutcome.builder().identifier(IDENTIFIER).build()));
    when(executionSweepingOutputService.listOutputsWithGivenNameAndSetupIds(
             any(), eq(FAILED_CHILDREN_OUTPUT), anyList()))
        .thenReturn(Collections.emptyList());

    doNothing().when(cdExpressionResolver).updateStoreConfigExpressions(any(), any());

    ServiceHookStepParameters stepParameters =
        ServiceHookStepParameters.builder()
            .identifier(IDENTIFIER)
            .order(0)
            .type(ServiceHookType.PRE_HOOK)
            .actions(Collections.singletonList(ServiceHookAction.STEADY_STATE_CHECK))
            .store(InlineStoreConfig.builder().content(ParameterField.createValueField("sup")).build())
            .build();
    StepResponse response =
        individualServiceHookStep.executeSync(ambiance, stepParameters, getStepInputPackage(), getPassThroughData());

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes()).hasSize(1);

    StepResponse.StepOutcome[] stepOutcomes = response.getStepOutcomes().toArray(new StepResponse.StepOutcome[1]);
    ServiceHookOutcome serviceHookOutcome = (ServiceHookOutcome) stepOutcomes[0].getOutcome();
    assertThat(serviceHookOutcome.getIdentifier()).isEqualTo(IDENTIFIER);

    assertThat(serviceHookOutcome.getStore().getKind()).isEqualTo(StoreConfigType.INLINE.getDisplayName());
    InlineStoreConfig store = (InlineStoreConfig) serviceHookOutcome.getStore();
    assertThat(store.getContent().getValue()).isEqualTo("sup");
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
        .build();
  }

  private StepInputPackage getStepInputPackage() {
    return StepInputPackage.builder().build();
  }

  private StepExceptionPassThroughData getPassThroughData() {
    return StepExceptionPassThroughData.builder().build();
  }
}
