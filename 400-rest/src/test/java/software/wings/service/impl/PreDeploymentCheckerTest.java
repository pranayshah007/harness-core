/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.service.impl.PipelinePreDeploymentValidator.APPROVAL_ERROR_MSG;
import static software.wings.service.impl.PreDeploymentCheckerTestHelper.getWorkflow;
import static software.wings.service.impl.WorkflowPreDeploymentValidator.getWorkflowRestrictedFeatureErrorMsg;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.configuration.DeployMode;
import io.harness.exception.WingsException;
import io.harness.limits.LimitCheckerFactory;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.beans.AccountType;
import software.wings.beans.LicenseInfo;
import software.wings.beans.Pipeline;
import software.wings.beans.Workflow;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.deployment.PreDeploymentChecker;
import software.wings.service.intfc.deployment.RateLimitCheck;

import com.google.inject.Inject;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class PreDeploymentCheckerTest extends WingsBaseTest {
  private static final String TEST_ACCOUNT_ID = "ACCOUNT_ID";
  private static final String TEST_ACCOUNT_NAME = "ACCOUNT_NAME";
  PreDeploymentCheckerTestHelper preDeploymentCheckerTestHelper = new PreDeploymentCheckerTestHelper();

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Mock private AccountService accountService;

  @Mock private LimitCheckerFactory limitCheckerFactory;

  @InjectMocks @Inject private PreDeploymentChecks preDeploymentChecker;
  @InjectMocks @Inject @RateLimitCheck private PreDeploymentChecker rateLimitChecker;
  @Mock private MainConfiguration mainConfiguration;

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void checkDeploymentRateLimit() {
    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES_ONPREM);

    String accountId = "some-account-id";
    rateLimitChecker.check(accountId);

    verifyNoInteractions(limitCheckerFactory);
  }

  @Before
  public void setup() {
    when(accountService.get(Mockito.any(String.class)))
        .thenReturn(anAccount()
                        .withCompanyName(TEST_ACCOUNT_NAME)
                        .withAccountName(TEST_ACCOUNT_NAME)
                        .withAccountKey(TEST_ACCOUNT_NAME)
                        .withLicenseInfo(getLicenseInfoForType(AccountType.COMMUNITY))
                        .build());
  }

  private LicenseInfo getLicenseInfoForType(@Nonnull String accountType) {
    LicenseInfo licenseInfo = getLicenseInfo();
    licenseInfo.setAccountType(accountType);
    return licenseInfo;
  }

  @Test()
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testWorkflowPreDeploymentCheckerViolation() {
    Workflow workflow = getWorkflow(true);
    thrown.expect(WingsException.class);
    thrown.expectMessage(getWorkflowRestrictedFeatureErrorMsg(workflow.getName()));
    when(accountService.getAccountType(ACCOUNT_ID)).thenReturn(Optional.of(AccountType.COMMUNITY));
    preDeploymentChecker.checkIfWorkflowUsingRestrictedFeatures(workflow);
  }

  @Test()
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testPipelinePreDeploymentCheckerViolation() {
    Pipeline pipeline = preDeploymentCheckerTestHelper.getPipeline();
    thrown.expect(WingsException.class);
    thrown.expectMessage(APPROVAL_ERROR_MSG);
    when(accountService.getAccountType(ACCOUNT_ID)).thenReturn(Optional.of(AccountType.COMMUNITY));
    preDeploymentChecker.checkIfPipelineUsingRestrictedFeatures(pipeline);
  }
}
