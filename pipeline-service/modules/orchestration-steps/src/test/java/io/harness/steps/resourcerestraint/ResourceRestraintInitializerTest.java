/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint;

import static io.harness.pms.utils.PmsConstants.QUEUING_RC_NAME;
import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.distribution.constraint.Constraint;
import io.harness.engine.observers.beans.OrchestrationStartInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint;
import io.harness.steps.resourcerestraint.service.ResourceRestraintService;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class ResourceRestraintInitializerTest extends OrchestrationStepsTestBase {
  public static final String ACCOUNT_ID = "ACCOUNT_ID";

  @InjectMocks private ResourceRestraintInitializer initializer;
  @Mock private ResourceRestraintService resourceRestraintService;

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldOnStartSaveResourceRestraint() {
    OrchestrationStartInfo osi = createOrchestrationStartInfo();
    initializer.onStart(osi);

    ArgumentCaptor<ResourceRestraint> argument = ArgumentCaptor.forClass(ResourceRestraint.class);
    verify(resourceRestraintService).save(argument.capture());

    ResourceRestraint value = argument.getValue();
    assertThat(value).isNotNull();
    assertThat(value.getName()).isEqualTo(QUEUING_RC_NAME);
    assertThat(value.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(value.getCapacity()).isEqualTo(1);
    assertThat(value.isHarnessOwned()).isTrue();
    assertThat(value.getStrategy()).isEqualTo(Constraint.Strategy.FIFO);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldOnStartCatchInvalidRequestException() {
    when(resourceRestraintService.save(notNull(ResourceRestraint.class))).thenThrow(new InvalidRequestException(""));
    initializer.onStart(createOrchestrationStartInfo());
    verify(resourceRestraintService).save(notNull(ResourceRestraint.class));
  }

  private OrchestrationStartInfo createOrchestrationStartInfo() {
    return OrchestrationStartInfo.builder()
        .ambiance(Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID).build())
        .build();
  }
}
