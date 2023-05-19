/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.NATHAN;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.RAJ;
import static io.harness.rule.OwnerRule.RUSHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.security.crypto.bcrypt.BCrypt.hashpw;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.configuration.DeployVariant;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.rule.Owner;

import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.loginSettings.LoginSettingsService;
import software.wings.beans.loginSettings.UserLockoutInfo;
import software.wings.security.authentication.recaptcha.FailedLoginAttemptCountChecker;
import software.wings.security.authentication.recaptcha.MaxLoginAttemptExceededException;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.security.crypto.bcrypt.BCrypt;

@OwnedBy(PL)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class PasswordBasedAuthHandlerTest extends CategoryTest {
  @Mock private MainConfiguration configuration;
  @Mock private LoginSettingsService loginSettingsService;
  @Mock private AuthenticationUtils authenticationUtils;
  @Mock private AccountService accountService;
  @Mock private FailedLoginAttemptCountChecker failedLoginAttemptCountChecker;
  @Mock private DomainWhitelistCheckerService domainWhitelistCheckerService;

  @InjectMocks @Inject @Spy PasswordBasedAuthHandler authHandler;

  public final String defaultAccountId = "kmpySmUISimoRrJL6NL73w";

  @Before
  public void setUp() {
    initMocks(this);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testInvalidArgument() {
    try {
      assertThat(authHandler.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.USER_PASSWORD);
      authHandler.authenticate();
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo(ErrorCode.INVALID_ARGUMENT.name());
    }

    try {
      authHandler.authenticate("test");
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo(ErrorCode.INVALID_ARGUMENT.name());
    }

    try {
      authHandler.authenticate("test", "test1", "test2", "test3");
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo(ErrorCode.INVALID_ARGUMENT.name());
    }
  }

  @Test
  @Owner(developers = RAJ)
  @Category(UnitTests.class)
  public void testCaptchaNotShownForCommunity() throws MaxLoginAttemptExceededException {
    User user = new User();
    user.setDefaultAccountId(defaultAccountId);
    user.setEmailVerified(true);
    user.setUuid(defaultAccountId);
    user.setPasswordHash(hashpw("notpassword", BCrypt.gensalt()));

    authHandler.setDeployVariant(DeployVariant.COMMUNITY);
    doReturn(user).when(authHandler).getUser(anyString());

    try {
      authHandler.authenticate("admin@harness.io", "admin", defaultAccountId);
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo(ErrorCode.INVALID_CREDENTIAL.name());
    }
    verify(failedLoginAttemptCountChecker, times(0)).check(user);
  }

  @Test
  @Owner(developers = RAJ)
  @Category(UnitTests.class)
  public void testCaptchShownForSaas() throws MaxLoginAttemptExceededException {
    User user = new User();
    user.setDefaultAccountId(defaultAccountId);
    user.setEmailVerified(true);
    user.setUuid(defaultAccountId);
    user.setPasswordHash(hashpw("notpassword", BCrypt.gensalt()));

    authHandler.setDeployVariant(DeployVariant.SAAS);
    doReturn(user).when(authHandler).getUser(anyString());

    try {
      authHandler.authenticate("admin@harness.io", "admin", defaultAccountId);
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo(ErrorCode.INVALID_CREDENTIAL.name());
    }
    verify(failedLoginAttemptCountChecker, times(1)).check(user);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testBasicTokenValidationNoUserFound() {
    try {
      doReturn(null).when(authHandler).getUser(anyString());
      authHandler.authenticate("admin@harness.io", "admin", defaultAccountId);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo(ErrorCode.USER_DOES_NOT_EXIST.name());
    }
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testBasicTokenValidationEmailNotVerified() {
    try {
      User user = new User();
      user.setDefaultAccountId(defaultAccountId);
      user.setUuid(defaultAccountId);
      doReturn(user).when(authHandler).getUser(anyString());
      authHandler.authenticate("admin@harness.io", "admin", defaultAccountId);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED.name());
    }
  }

  @Test
  @Owner(developers = NATHAN)
  @Category(UnitTests.class)
  public void testBasicTokenValidationEmailNotRequired() {
    User mockUser = new User();
    mockUser.setEmailVerified(true);
    mockUser.setUuid("TestUID");
    mockUser.setDefaultAccountId(defaultAccountId);
    mockUser.setPasswordHash("$2a$10$Rf/.q4HvUkS7uG2Utdkk7.jLnqnkck5ruH/vMrHjGVk4R9mL8nQE2");
    when(configuration.getPortal()).thenReturn(mock(PortalConfig.class));
    when(authenticationUtils.getDefaultAccount(any(User.class))).thenReturn(new Account());
    when(loginSettingsService.isUserLocked(any(User.class), any(Account.class))).thenReturn(false);
    doNothing().when(loginSettingsService).updateUserLockoutInfo(any(User.class), any(Account.class), anyInt());
    doReturn(mockUser).when(authHandler).getUser(anyString());
    when(domainWhitelistCheckerService.isDomainWhitelisted(mockUser)).thenReturn(true);
    when(accountService.getFromCacheWithFallback(anyString()))
        .thenReturn(Account.Builder.anAccount().withCreatedFromNG(true).build());

    User authenticatedUser = authHandler.authenticate("admin@harness.io", "admin", defaultAccountId).getUser();
    assertThat(authenticatedUser).isNotNull();
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testBasicTokenValidationInvalidCredentials() throws MaxLoginAttemptExceededException {
    try {
      doNothing().when(failedLoginAttemptCountChecker).check(Mockito.any(User.class));

      User mockUser = mock(User.class);
      when(mockUser.isEmailVerified()).thenReturn(true);
      when(mockUser.getUserLockoutInfo()).thenReturn(new UserLockoutInfo());
      when(mockUser.getDefaultAccountId()).thenReturn(defaultAccountId);
      when(mockUser.getUuid()).thenReturn(defaultAccountId);
      doNothing().when(loginSettingsService).updateUserLockoutInfo(any(User.class), any(Account.class), anyInt());
      doReturn(mockUser).when(authHandler).getUser(anyString());
      when(mockUser.getPasswordHash()).thenReturn("$2a$10$Rf/.q4HvUkS7uG2Utdkk7.jLnqnkck5ruH/vMrHjGVk4R9mL8nQE2");
      when(domainWhitelistCheckerService.isDomainWhitelisted(mockUser)).thenReturn(true);
      authHandler.authenticate("admin@harness.io", "admintest", defaultAccountId);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException e) {
      assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIAL);
    }
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testFailedAttemptLimitExceeded() throws MaxLoginAttemptExceededException {
    try {
      doThrow(new MaxLoginAttemptExceededException(3, 4))
          .when(failedLoginAttemptCountChecker)
          .check(Mockito.any(User.class));

      User mockUser = mock(User.class);
      when(mockUser.getDefaultAccountId()).thenReturn(defaultAccountId);
      when(mockUser.getUuid()).thenReturn(defaultAccountId);
      when(mockUser.isEmailVerified()).thenReturn(true);
      when(mockUser.getUserLockoutInfo()).thenReturn(new UserLockoutInfo());
      doNothing().when(loginSettingsService).updateUserLockoutInfo(any(User.class), any(Account.class), anyInt());
      doReturn(mockUser).when(authHandler).getUser(anyString());
      when(mockUser.getPasswordHash()).thenReturn("$2a$10$Rf/.q4HvUkS7uG2Utdkk7.jLnqnkck5ruH/vMrHjGVk4R9mL8nQE2");
      when(domainWhitelistCheckerService.isDomainWhitelisted(mockUser)).thenReturn(true);
      authHandler.authenticate("admin@harness.io", "admintest", defaultAccountId);
      failBecauseExceptionWasNotThrown(WingsException.class);

    } catch (WingsException e) {
      assertThat(e.getCode()).isEqualTo(ErrorCode.MAX_FAILED_ATTEMPT_COUNT_EXCEEDED);
    }
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testBasicTokenValidationValidCredentials() {
    User mockUser = new User();
    mockUser.setEmailVerified(true);
    mockUser.setUuid("TestUID");
    mockUser.setDefaultAccountId(defaultAccountId);
    mockUser.setPasswordHash("$2a$10$Rf/.q4HvUkS7uG2Utdkk7.jLnqnkck5ruH/vMrHjGVk4R9mL8nQE2");
    when(configuration.getPortal()).thenReturn(mock(PortalConfig.class));
    when(authenticationUtils.getDefaultAccount(any(User.class))).thenReturn(new Account());
    when(loginSettingsService.isUserLocked(any(User.class), any(Account.class))).thenReturn(false);
    doNothing().when(loginSettingsService).updateUserLockoutInfo(any(User.class), any(Account.class), anyInt());
    doReturn(mockUser).when(authHandler).getUser(anyString());
    when(domainWhitelistCheckerService.isDomainWhitelisted(mockUser)).thenReturn(true);
    User user = authHandler.authenticate("admin@harness.io", "admin", defaultAccountId).getUser();
    assertThat(user).isNotNull();
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSaasUserPasswordNoPasswordHash() throws MaxLoginAttemptExceededException {
    User user = new User();
    user.setDefaultAccountId(defaultAccountId);
    user.setEmailVerified(true);
    user.setUuid(defaultAccountId);
    user.setPasswordHash(null);

    authHandler.setDeployVariant(DeployVariant.SAAS);
    doReturn(user).when(authHandler).getUser(anyString());

    try {
      authHandler.authenticate("admin@harness.io", "admin", defaultAccountId);
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo(ErrorCode.INVALID_CREDENTIAL.name());
    }
  }
}
