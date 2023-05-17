/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers;

import static io.harness.repositories.custom.NGTriggerRepositoryCustomImpl.updateTriggerStatus;
import static io.harness.rule.OwnerRule.VED;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.metadata.status.PollingSubscriptionStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.StatusResult;
import io.harness.ngtriggers.beans.entity.metadata.status.TriggerStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.ValidationStatus;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NGTriggerRepositoryCustomImplTest extends CategoryTest {
  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testUpdateTriggerStatus() {
    List<NGTriggerEntity> triggerEntities = new ArrayList<>();

    TriggerStatus triggerStatus =
        TriggerStatus.builder()
            .pollingSubscriptionStatus(
                PollingSubscriptionStatus.builder().statusResult(StatusResult.SUCCESS).detailedMessage(null).build())
            .validationStatus(
                ValidationStatus.builder().statusResult(StatusResult.FAILED).detailedMessage("failed").build())
            .webhookAutoRegistrationStatus(null)
            .build();

    NGTriggerEntity trigger = NGTriggerEntity.builder()
                                  .accountId("accountId")
                                  .orgIdentifier("orgIdentifier")
                                  .projectIdentifier("projIdentifier")
                                  .deleted(false)
                                  .enabled(true)
                                  .name("Trigger-123")
                                  .identifier("trigg")
                                  .type(NGTriggerType.ARTIFACT)
                                  .triggerStatus(triggerStatus)
                                  .build();

    triggerEntities.add(trigger);

    triggerEntities = updateTriggerStatus(triggerEntities);

    Assertions.assertThat(triggerEntities.get(0).getTriggerStatus().getStatus()).isEqualTo(StatusResult.FAILED);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testUpdateTriggerStatus_2() {
    List<NGTriggerEntity> triggerEntities = new ArrayList<>();

    TriggerStatus triggerStatus =
        TriggerStatus.builder()
            .pollingSubscriptionStatus(
                PollingSubscriptionStatus.builder().statusResult(StatusResult.SUCCESS).detailedMessage(null).build())
            .validationStatus(
                ValidationStatus.builder().statusResult(StatusResult.SUCCESS).detailedMessage(null).build())
            .webhookAutoRegistrationStatus(null)
            .build();

    NGTriggerEntity trigger = NGTriggerEntity.builder()
                                  .accountId("accountId")
                                  .orgIdentifier("orgIdentifier")
                                  .projectIdentifier("projIdentifier")
                                  .deleted(false)
                                  .enabled(true)
                                  .name("Trigger-123")
                                  .identifier("trigg")
                                  .type(NGTriggerType.ARTIFACT)
                                  .triggerStatus(triggerStatus)
                                  .build();

    triggerEntities.add(trigger);

    triggerEntities = updateTriggerStatus(triggerEntities);

    Assertions.assertThat(triggerEntities.get(0).getTriggerStatus().getStatus()).isEqualTo(StatusResult.SUCCESS);
  }
}
