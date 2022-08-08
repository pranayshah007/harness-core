/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.signup;

import static io.harness.annotations.dev.HarnessModule._950_NG_SIGNUP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.AMAN;

import static software.wings.beans.UserInvite.UserInviteBuilder.anUserInvite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.configuration.DeployMode;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.exception.SignupException;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.beans.UserInvite;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.SignupService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@Slf4j
@OwnedBy(PL)
@TargetModule(_950_NG_SIGNUP)
public class OnpremSignupHandlerTest extends WingsBaseTest {
  private static final String EMAIL = "abc@harness.io";
  private static final String UUID = "uuid";
  private static final String PASSWORD = "12345678";
  public static final String COMPANY_NAME = "abc";
  public static final String NAME = "test";

  @Mock private SignupService signupService;
  @Mock private UserService userService;
  @Mock private EventPublishHelper eventPublishHelper;
  @Mock private MainConfiguration configuration;
  @Mock private AccountService accountService;
  @Mock private NgLicenseHttpClient ngLicenseHttpClient;

  @InjectMocks @Inject OnpremSignupHandler onpremSignupHandler;

  private UserInvite createUserInvite() {
    UserInvite userInvite = anUserInvite()
                                .withCompanyName(COMPANY_NAME)
                                .withAccountName(COMPANY_NAME)
                                .withEmail(EMAIL)
                                .withName(NAME)
                                .build();
    userInvite.setPassword(PASSWORD.toCharArray());
    return userInvite;
  }

  @Before
  public void setup() {
    doNothing().when(signupService).validateCluster();
    doNothing().when(signupService).validateEmail(EMAIL);
    when(signupService.getUserInviteByEmail(EMAIL)).thenReturn(null);
    doNothing().when(userService).sendVerificationEmail(any(UserInvite.class), anyString(), Mockito.anyMap());

    when(userService.saveUserInvite(any(UserInvite.class))).thenReturn(UUID);
    when(configuration.getDeployMode()).thenReturn(DeployMode.KUBERNETES_ONPREM);
    doNothing()
        .when(eventPublishHelper)
        .publishTrialUserSignupEvent(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testNewUserInviteHandleShouldSucceed() {
    when(signupService.getUserInviteByEmail(EMAIL)).thenReturn(null);
    doNothing().when(accountService).updateFeatureFlagsForOnPremAccount();

    // Assertion
    assertThat(onpremSignupHandler.handle(createUserInvite())).isTrue();
    verify(userService, Mockito.times(1)).saveUserInvite(any(UserInvite.class));
    verify(accountService, Mockito.times(1)).updateFeatureFlagsForOnPremAccount();
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testNewUserInviteCompleteShouldFail() {
    try {
      assertThat(onpremSignupHandler.completeSignup(null, null));
      fail("Expcted the above call to fail.");
    } catch (SignupException se) {
      log.info("Expected behaviour");
    }
  }

  private void fail(String message) {
    throw new RuntimeException(message);
  }
}
