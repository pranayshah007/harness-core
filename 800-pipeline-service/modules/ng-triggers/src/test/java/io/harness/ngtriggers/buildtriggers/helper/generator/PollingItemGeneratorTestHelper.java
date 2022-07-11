/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helper.generator;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.polling.contracts.Category;
import io.harness.polling.contracts.PollingItem;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class PollingItemGeneratorTestHelper {
  public void baseAssert(PollingItem pollingItem, Category category) {
    assertThat(pollingItem).isNotNull();
    assertThat(pollingItem.getCategory() == category).isTrue();
    assertThat(pollingItem.getSignature()).isEqualTo("sig1");
    assertThat(pollingItem.getQualifier().getAccountId()).isEqualTo("acc");
    assertThat(pollingItem.getQualifier().getOrganizationId()).isEqualTo("org");
    assertThat(pollingItem.getQualifier().getProjectId()).isEqualTo("proj");
  }

  public void validateBuildType(BuildTriggerOpsData buildTriggerOpsData, BuildTriggerHelper buildTriggerHelper) {
    try {
      buildTriggerHelper.validateBuildType(buildTriggerOpsData);
    } catch (Exception e) {
      fail("Exception not expected");
    }
  }
}
