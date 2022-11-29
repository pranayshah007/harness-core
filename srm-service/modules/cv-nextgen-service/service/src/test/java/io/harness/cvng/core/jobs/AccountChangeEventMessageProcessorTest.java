/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.VUK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AccountChangeEventMessageProcessorTest extends CvNextGenTestBase {
  @Inject private AccountChangeEventMessageProcessor accountChangeEventMessageProcessor;
  @Inject private CVConfigService cvConfigService;
  private BuilderFactory builderFactory;
  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
  }
  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testProcessDeleteAction() {
    String accountId = generateUuid();
    CVConfig cvConfig = createCVConfig(accountId);
    cvConfigService.save(cvConfig);
    accountChangeEventMessageProcessor.processDeleteAction(
        AccountEntityChangeDTO.newBuilder().setAccountId(accountId).build());

    assertThat(cvConfigService.get(cvConfig.getUuid())).isNull();

    // For every message processing, idemptotency is assumed - Redelivery of a message produces the same result and
    // there are no side effects
    CVConfig retrievedCVConfig = cvConfigService.get(cvConfig.getUuid());

    accountChangeEventMessageProcessor.processDeleteAction(
        AccountEntityChangeDTO.newBuilder().setAccountId(accountId).build());

    assertThat(cvConfigService.get(cvConfig.getUuid())).isNull();
    assertThat(retrievedCVConfig).isNull();
  }
  private CVConfig createCVConfig(String accountId) {
    return builderFactory.splunkCVConfigBuilder().accountId(accountId).build();
  }
}
