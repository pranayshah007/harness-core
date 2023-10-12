/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.features;

import static io.harness.rule.OwnerRule.ANKIT;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.service.impl.PreDeploymentCheckerTestHelper.getWorkflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse.PageResponseBuilder;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AccountType;
import software.wings.beans.LicenseInfo;
import software.wings.beans.account.AccountStatus;
import software.wings.features.api.PremiumFeature;
import software.wings.features.api.Usage;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.WorkflowService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collection;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class FlowControlFeatureTest extends WingsBaseTest {
  private static final String TEST_ACCOUNT_ID = "ACCOUNT_ID";

  @Mock private WorkflowService workflowService;
  @InjectMocks @Inject @Named(FlowControlFeature.FEATURE_NAME) private PremiumFeature flowControlFeature;
  @Inject AccountService accountService;

  @Before
  public void setUp() {
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.COMMUNITY);
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setLicenseUnits(100);
    licenseInfo.setExpiryTime(System.nanoTime());

    accountService.save(anAccount()
                            .withUuid(TEST_ACCOUNT_ID)
                            .withCompanyName("Harness")
                            .withAccountName("Harness")
                            .withAccountKey("ACCOUNT_KEY")
                            .withLicenseInfo(licenseInfo)
                            .build(),
        false);
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void workflowWithFlowControl() {
    when(workflowService.listWorkflows(Mockito.any(PageRequest.class)))
        .thenReturn(
            PageResponseBuilder.aPageResponse().withResponse(Collections.singletonList(getWorkflow(true))).build());

    Collection<Usage> disallowedUsages = flowControlFeature.getDisallowedUsages(TEST_ACCOUNT_ID, AccountType.COMMUNITY);

    assertThat(disallowedUsages).isNotNull();
    assertThat(disallowedUsages).hasSize(1);
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void workflowWithoutFlowControl() {
    when(workflowService.listWorkflows(Mockito.any(PageRequest.class)))
        .thenReturn(
            PageResponseBuilder.aPageResponse().withResponse(Collections.singletonList(getWorkflow(false))).build());

    Collection<Usage> disallowedUsages = flowControlFeature.getDisallowedUsages(TEST_ACCOUNT_ID, AccountType.COMMUNITY);

    assertThat(disallowedUsages).isNotNull();
    assertThat(disallowedUsages).isEmpty();
  }
}
