/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.SHALINI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.persistance.GitSyncablePersistenceConfig;
import io.harness.notification.NotificationChannelPersistenceConfig;
import io.harness.pms.outbox.PipelineOutboxPersistenceConfig;
import io.harness.rule.Owner;
import io.harness.springdata.SpringPersistenceConfig;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelinePersistenceModuleTest extends CategoryTest {
  PipelinePersistenceModule pipelinePersistenceModule = new PipelinePersistenceModule();
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetConfigClasses() {
    Class<?>[] classes = pipelinePersistenceModule.getConfigClasses();
    assertThat(classes).contains(SpringPersistenceConfig.class);
    assertThat(classes).contains(NotificationChannelPersistenceConfig.class);
    assertThat(classes).contains(GitSyncablePersistenceConfig.class);
    assertThat(classes).contains(PipelineOutboxPersistenceConfig.class);
  }
}
