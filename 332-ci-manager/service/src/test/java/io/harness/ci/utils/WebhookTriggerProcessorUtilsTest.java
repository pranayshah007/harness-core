/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import static io.harness.rule.OwnerRule.DEV_MITTAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CI)
@RunWith(MockitoJUnitRunner.class)
public class WebhookTriggerProcessorUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetShortCommitSha() {
    assertThat(WebhookTriggerProcessorUtils.getShortCommitSha("abcdefgh")).isEqualTo("abcdefg");
    assertThat(WebhookTriggerProcessorUtils.getShortCommitSha("abcdefghndjvjf")).isEqualTo("abcdefg");
    assertThat(WebhookTriggerProcessorUtils.getShortCommitSha("abcdefg")).isEqualTo("abcdefg");
    assertThat(WebhookTriggerProcessorUtils.getShortCommitSha("abcdef")).isEqualTo("abcdef");
    assertThat(WebhookTriggerProcessorUtils.getShortCommitSha("a")).isEqualTo("a");
    assertThat(WebhookTriggerProcessorUtils.getShortCommitSha(null)).isEqualTo(null);
    assertThat(WebhookTriggerProcessorUtils.getShortCommitSha("")).isEqualTo(null);
  }
}
