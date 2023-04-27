/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.governance.pipeline.service;

import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.beans.Account.Builder.anAccount;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.governance.pipeline.service.model.PipelineGovernanceConfig;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.Event;
import software.wings.beans.LicenseInfo;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class PipelineGovernanceServiceImplTest extends WingsBaseTest {
  @Inject @InjectMocks private PipelineGovernanceService pipelineGovernanceService;
  @Inject private HPersistence persistence;
  @Inject private AccountService accountService;
  @Mock private AuditServiceHelper auditServiceHelper;

  private final String SOME_ACCOUNT_ID =
      randomAlphanumeric(5) + "-some-account-id-" + PipelineGovernanceServiceImplTest.class.getSimpleName();

  private boolean accountAdded;

  @Before
  public void init() {
    if (!accountAdded) {
      long tooFarTime = 1998195261000L;
      LicenseInfo licenseInfo =
          LicenseInfo.builder().accountType(AccountType.PAID).expiryTime(tooFarTime).licenseUnits(50).build();
      Account account = anAccount()
                            .withAccountName("some-account-name")
                            .withCompanyName("some-co-name")
                            .withUuid(SOME_ACCOUNT_ID)
                            .withLicenseInfo(licenseInfo)
                            .build();

      accountService.save(account, false);
      accountAdded = true;
    }

    persistence.delete(persistence.createQuery(PipelineGovernanceConfig.class));
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testList() {
    List<PipelineGovernanceConfig> initialList = pipelineGovernanceService.list(SOME_ACCOUNT_ID);

    PipelineGovernanceConfig config = new PipelineGovernanceConfig(
        null, SOME_ACCOUNT_ID, "name", "description", Collections.emptyList(), Collections.emptyList(), true);

    pipelineGovernanceService.add(SOME_ACCOUNT_ID, config);

    List<PipelineGovernanceConfig> list = pipelineGovernanceService.list(SOME_ACCOUNT_ID);
    assertThat(list.size()).isEqualTo(initialList.size() + 1);
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)

  public void testAddAndUpdate() {
    PipelineGovernanceConfig config = new PipelineGovernanceConfig(
        null, SOME_ACCOUNT_ID, "name", "description", Collections.emptyList(), Collections.emptyList(), true);
    PipelineGovernanceConfig addedConfig = pipelineGovernanceService.add(SOME_ACCOUNT_ID, config);

    assertThat(addedConfig.getUuid()).isNotNull();
    assertThat(pipelineGovernanceService.get(addedConfig.getUuid())).isNotNull();
    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(eq(SOME_ACCOUNT_ID), eq(null), eq(addedConfig), eq(Event.Type.CREATE));

    PipelineGovernanceConfig updatedConfig =
        new PipelineGovernanceConfig(addedConfig.getUuid(), addedConfig.getAccountId(), addedConfig.getName(),
            "Test Description", addedConfig.getRules(), addedConfig.getRestrictions(), !addedConfig.isEnabled());
    updatedConfig = pipelineGovernanceService.add(SOME_ACCOUNT_ID, updatedConfig);

    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(
            eq(SOME_ACCOUNT_ID), eq(addedConfig), eq(updatedConfig), eq(Event.Type.UPDATE));
  }
}
