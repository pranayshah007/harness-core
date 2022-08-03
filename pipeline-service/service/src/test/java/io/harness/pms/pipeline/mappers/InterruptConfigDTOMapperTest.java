/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.interrupts.InterruptEffect;
import io.harness.interrupts.InterruptEffectDTO;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.interrupts.AdviserIssuer;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.ManualIssuer;
import io.harness.pms.contracts.interrupts.RetryInterruptConfig;
import io.harness.pms.contracts.interrupts.TimeoutIssuer;
import io.harness.pms.contracts.interrupts.TriggerIssuer;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class InterruptConfigDTOMapperTest {
  public static final String SOME_INTERRUPT_ID = "SOME_INTERRUPT_ID";
  public static final String SOME_RETRY_ID = "SOME_RETRY_ID";
  public static final InterruptType SOME_INTERRUPT_TYPE = InterruptType.ABORT;
  public static final long SOME_TIMESTAMP = 21;
  public static final AdviseType SOME_ADVISE_TYPE = AdviseType.INTERVENTION_WAIT;
  private static final String SOME_TRIGGER_REF = "SOME_TRIGGER_REF";
  private static final String SOME_IDENTIFIER = "SOME_IDENTIFIER";
  private static final String SOME_TYPE = "SOME_TYPE";
  private static final String SOME_EMAIL_ID = "SOME_EMAIL_ID";
  private static final String SOME_USER_ID = "SOME_USER_ID";
  private static final String SOME_TIMEOUT_INSTANCE_ID = "SOME_TIMEOUT_INSTANCE_ID";

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testToInterruptEffectDTOList() {
    List<InterruptEffectDTO> interruptEffectDTOList = InterruptConfigDTOMapper.toInterruptEffectDTOList(null);
    assertThat(interruptEffectDTOList).isEmpty();

    interruptEffectDTOList = InterruptConfigDTOMapper.toInterruptEffectDTOList(new ArrayList<>());
    assertThat(interruptEffectDTOList).isEmpty();

    InterruptEffect interruptEffect = InterruptEffect.builder()
                                          .interruptId(SOME_INTERRUPT_ID)
                                          .interruptType(SOME_INTERRUPT_TYPE)
                                          .tookEffectAt(SOME_TIMESTAMP)
                                          .build();

    interruptEffectDTOList = InterruptConfigDTOMapper.toInterruptEffectDTOList(Arrays.asList(interruptEffect));
    assertThat(interruptEffectDTOList).hasSize(1);
    InterruptEffectDTO interruptEffectDTO = interruptEffectDTOList.get(0);
    assertThat(interruptEffectDTO.getInterruptId()).isEqualTo(SOME_INTERRUPT_ID);
    assertThat(interruptEffectDTO.getTookEffectAt()).isEqualTo(SOME_TIMESTAMP);
    assertThat(interruptEffectDTO.getInterruptType()).isEqualTo(SOME_INTERRUPT_TYPE);

    testToAdviserIssuer();
  }

  private void testToAdviserIssuer() {
    InterruptEffect interruptEffect;
    List<InterruptEffectDTO> interruptEffectDTOList;
    InterruptEffectDTO interruptEffectDTO;
    RetryInterruptConfig retryInterruptConfig = RetryInterruptConfig.newBuilder().setRetryId(SOME_RETRY_ID).build();
    AdviserIssuer adviserIssuer = AdviserIssuer.newBuilder().setAdviserType(SOME_ADVISE_TYPE).build();
    IssuedBy issuedBy = IssuedBy.newBuilder().setAdviserIssuer(adviserIssuer).build();
    InterruptConfig interruptConfig =
        InterruptConfig.newBuilder().setRetryInterruptConfig(retryInterruptConfig).setIssuedBy(issuedBy).build();
    interruptEffect = InterruptEffect.builder().interruptConfig(interruptConfig).build();
    interruptEffectDTOList = InterruptConfigDTOMapper.toInterruptEffectDTOList(Arrays.asList(interruptEffect));
    interruptEffectDTO = interruptEffectDTOList.get(0);
    assertThat(interruptEffectDTO.getInterruptConfig()).isNotNull();
    io.harness.interrupts.IssuedBy actualIssuedBy = interruptEffectDTO.getInterruptConfig().getIssuedBy();
    assertThat(actualIssuedBy).isNotNull();
    assertThat(interruptEffectDTO.getInterruptConfig().getRetryInterruptConfig().getRetryId()).isEqualTo(SOME_RETRY_ID);
    assertThat(actualIssuedBy.getAdviserIssuer().getAdviseType()).isEqualTo(SOME_ADVISE_TYPE);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testToTriggerIssuer() {
    TriggerIssuer triggerIssuer = TriggerIssuer.newBuilder().setTriggerRef(SOME_TRIGGER_REF).build();
    IssuedBy issuedBy = IssuedBy.newBuilder().setTriggerIssuer(triggerIssuer).build();
    InterruptConfig interruptConfig = InterruptConfig.newBuilder().setIssuedBy(issuedBy).build();
    InterruptEffect interruptEffect = InterruptEffect.builder().interruptConfig(interruptConfig).build();
    List<InterruptEffectDTO> interruptEffectDTOList =
        InterruptConfigDTOMapper.toInterruptEffectDTOList(Arrays.asList(interruptEffect));
    io.harness.interrupts.IssuedBy actualIssuedBy = interruptEffectDTOList.get(0).getInterruptConfig().getIssuedBy();
    assertThat(actualIssuedBy.getTriggerIssuer().getTriggerRef()).isEqualTo(SOME_TRIGGER_REF);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testToManualIssuer() {
    ManualIssuer manualIssuer = ManualIssuer.newBuilder()
                                    .setIdentifier(SOME_IDENTIFIER)
                                    .setType(SOME_TYPE)
                                    .setEmailId(SOME_EMAIL_ID)
                                    .setUserId(SOME_USER_ID)
                                    .build();
    IssuedBy issuedBy = IssuedBy.newBuilder().setManualIssuer(manualIssuer).build();
    InterruptConfig interruptConfig = InterruptConfig.newBuilder().setIssuedBy(issuedBy).build();
    InterruptEffect interruptEffect = InterruptEffect.builder().interruptConfig(interruptConfig).build();
    List<InterruptEffectDTO> interruptEffectDTOList =
        InterruptConfigDTOMapper.toInterruptEffectDTOList(Arrays.asList(interruptEffect));
    io.harness.interrupts.IssuedBy actualIssuedBy = interruptEffectDTOList.get(0).getInterruptConfig().getIssuedBy();
    assertThat(actualIssuedBy.getManualIssuer().getIdentifier()).isEqualTo(SOME_IDENTIFIER);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testToTimeoutIssuer() {
    TimeoutIssuer timeoutIssuer = TimeoutIssuer.newBuilder().setTimeoutInstanceId(SOME_TIMEOUT_INSTANCE_ID).build();
    IssuedBy issuedBy = IssuedBy.newBuilder().setTimeoutIssuer(timeoutIssuer).build();
    InterruptConfig interruptConfig = InterruptConfig.newBuilder().setIssuedBy(issuedBy).build();
    InterruptEffect interruptEffect = InterruptEffect.builder().interruptConfig(interruptConfig).build();
    List<InterruptEffectDTO> interruptEffectDTOList =
        InterruptConfigDTOMapper.toInterruptEffectDTOList(Arrays.asList(interruptEffect));
    io.harness.interrupts.IssuedBy actualIssuedBy = interruptEffectDTOList.get(0).getInterruptConfig().getIssuedBy();
    assertThat(actualIssuedBy.getTimeoutIssuer().getTimeoutInstanceId()).isEqualTo(SOME_TIMEOUT_INSTANCE_ID);
  }
}
