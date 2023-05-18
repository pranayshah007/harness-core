/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.signup;

import static io.harness.rule.OwnerRule.AMAN;

import static software.wings.beans.UserInvite.UserInviteBuilder.anUserInvite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.resources.UserResource.UpdatePasswordRequest;
import software.wings.service.intfc.SignupService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import java.io.UnsupportedEncodingException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class AzureSignupHandlerTest extends WingsBaseTest {
  private static final String EMAIL = "abc@harness.io";
  private static final String UUID = "uuid";
  private static final String PASSWORD = "12345678";
  public static final String COMPANY_NAME = "abc";
  public static final String NAME = "test";

  @Mock private SignupService signupService;
  @Mock private UserService userService;
  @Mock private EventPublishHelper eventPublishHelper;
  @Mock private SignupSpamChecker spamChecker;
  @Mock private AzureMarketplaceIntegrationService azureMarketplaceIntegrationService;
  @Mock private MainConfiguration configuration;

  @InjectMocks @Inject AzureMarketplaceSignupHandler azureMarketplaceSignupHandler;

  @Captor ArgumentCaptor<UserInvite> userInviteCaptor;

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
    doNothing().when(userService).sendVerificationEmail(Mockito.any(UserInvite.class), anyString(), Mockito.anyMap());

    when(userService.saveUserInvite(Mockito.any(UserInvite.class))).thenReturn(UUID);
    doNothing()
        .when(eventPublishHelper)
        .publishTrialUserSignupEvent(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testNewUserInviteHandleShouldSucceed() {
    when(signupService.getUserInviteByEmail(EMAIL)).thenReturn(null);

    // Assertion
    assertThat(azureMarketplaceSignupHandler.handle(createUserInvite())).isTrue();
    verify(userService, Mockito.times(1)).saveUserInvite(Mockito.any(UserInvite.class));
    verify(userService, Mockito.times(1)).sendVerificationEmail(Mockito.any(UserInvite.class), anyString(), anyMap());
    verify(eventPublishHelper, Mockito.times(1))
        .publishTrialUserSignupEvent(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testExistingUserInviteHandleShouldSucceed() {
    UserInvite existingUserInvite = createUserInvite();
    existingUserInvite.setCompleted(false);
    existingUserInvite.setUuid(UUID);

    when(spamChecker.isSpam(Mockito.any(UserInvite.class))).thenReturn(false);
    when(signupService.getUserInviteByEmail(EMAIL)).thenReturn(existingUserInvite);

    // Assertions
    assertThat(azureMarketplaceSignupHandler.handle(createUserInvite())).isTrue();
    verify(spamChecker, Mockito.times(1)).isSpam(Mockito.any(UserInvite.class));
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testExistingCompletedUserInviteHandleShouldSucceed() {
    UserInvite existingUserInvite = createUserInvite();
    existingUserInvite.setCompleted(true);
    existingUserInvite.setUuid("uuid");

    when(signupService.getUserInviteByEmail(EMAIL)).thenReturn(existingUserInvite);

    when(spamChecker.isSpam(Mockito.any(UserInvite.class))).thenReturn(false);
    doNothing().when(signupService).sendTrialSignupCompletedEmail(existingUserInvite);
    when(userService.saveUserInvite(Mockito.any(UserInvite.class))).thenReturn(UUID);

    // Assertions
    assertThat(azureMarketplaceSignupHandler.handle(createUserInvite())).isTrue();
    verify(spamChecker, Mockito.times(1)).isSpam(Mockito.any(UserInvite.class));
    verify(signupService, Mockito.times(1)).sendTrialSignupCompletedEmail(Mockito.any(UserInvite.class));
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testCompleteInviteFlowShouldSucceed() throws UnsupportedEncodingException {
    UpdatePasswordRequest passwordRequest = null;

    UserInvite existingUserInvite = createUserInvite();
    existingUserInvite.setCompleted(false);
    existingUserInvite.setUuid(UUID);

    doNothing().when(signupService).validatePassword(Mockito.any());
    when(signupService.getUserInviteByEmail(EMAIL)).thenReturn(existingUserInvite);

    when(userService.completePaidSignupAndSignIn(userInviteCaptor.capture()))
        .thenReturn(User.Builder.anUser().email(EMAIL).build());
    when(signupService.getEmail(anyString())).thenReturn(EMAIL);
    User user = azureMarketplaceSignupHandler.completeSignup(null, "randomToken");
    assertThat(String.valueOf(user.getEmail())).isEqualTo(EMAIL);
    assertThat(String.valueOf(userInviteCaptor.getValue().getPassword())).isEqualTo(PASSWORD);
    verify(userService, Mockito.times(1)).completePaidSignupAndSignIn(Mockito.any(UserInvite.class));
  }
}
