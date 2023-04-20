/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.signup;

import static io.harness.annotations.dev.HarnessModule._950_NG_SIGNUP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.common.beans.Generation.CG;
import static io.harness.ng.core.common.beans.Generation.NG;
import static io.harness.rule.OwnerRule.AMAN;
import static io.harness.rule.OwnerRule.KAPIL_GARG;
import static io.harness.rule.OwnerRule.SHASHANK;

import static software.wings.beans.UserInvite.UserInviteBuilder.anUserInvite;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
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
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.SignupService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.util.Optional;
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
  @Mock private WingsPersistence wingsPersistence;
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

  @Test(expected = SignupException.class)
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testNewUserInviteCompleteShouldFail() {
    onpremSignupHandler.completeSignup(null, null);
  }

  @Test
  @Owner(developers = {KAPIL_GARG, SHASHANK})
  @Category(UnitTests.class)
  public void handle_noExistingAccount_signupCompleted() {
    when(accountService.getOnPremAccount()).thenReturn(Optional.empty());
    UserInvite userInvite = createUserInvite();
    setupUserAndAccountQueries(0, 0);

    onpremSignupHandler.handle(userInvite);
    Mockito.verify(userService, times(1)).saveUserInvite(userInvite);
    Mockito.verify(userService, times(1)).completeTrialSignupAndSignIn(userInvite);
    Mockito.verify(userService, times(0)).createNewUserAndSignIn(Mockito.any(), Mockito.anyString(), Mockito.any());
  }

  @Test
  @Owner(developers = {KAPIL_GARG, SHASHANK})
  @Category(UnitTests.class)
  public void handle_existingAccount_signupCompleted() {
    Account account = Account.Builder.anAccount().build();
    account.setUuid("test");
    when(accountService.getOnPremAccount()).thenReturn(Optional.of(account));
    UserInvite userInvite = createUserInvite();
    setupUserAndAccountQueries(0, 1);

    onpremSignupHandler.handle(userInvite);
    Mockito.verify(userService, times(0)).saveUserInvite(userInvite);
    Mockito.verify(userService, times(0)).completeTrialSignupAndSignIn(userInvite);
    Mockito.verify(userService, times(1))
        .createNewUserAndSignIn(Mockito.any(), Mockito.eq(account.getUuid()), Mockito.any());
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testUserCreatedWithAccountLevelDataForCG() {
    Account account = Account.Builder.anAccount().build();
    account.setUuid("test");
    when(accountService.getOnPremAccount()).thenReturn(Optional.of(account));
    UserInvite userInvite = createUserInvite();
    setupUserAndAccountQueries(0, 1);
    onpremSignupHandler.handle(userInvite);

    Mockito.verify(userService, times(1))
        .createNewUserAndSignIn(Mockito.any(), Mockito.eq(account.getUuid()), Mockito.eq(CG));
    Mockito.verify(userService, times(0))
        .createNewUserAndSignIn(Mockito.any(), Mockito.eq(account.getUuid()), Mockito.eq(NG));
  }

  @Test(expected = SignupException.class)
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void handle_existingUser_signupFailed() {
    setupUserAndAccountQueries(1, 0);
    UserInvite userInvite = createUserInvite();
    onpremSignupHandler.handle(userInvite);
  }

  @Test(expected = SignupException.class)
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void handle_existingMultipleAccounts_signupFailed() {
    setupUserAndAccountQueries(0, 2);
    UserInvite userInvite = createUserInvite();
    onpremSignupHandler.handle(userInvite);
  }

  private void setupUserAndAccountQueries(long userCount, long accountCount) {
    Query mockUserQuery = Mockito.mock(Query.class);
    Query mockAccountQuery = Mockito.mock(Query.class);
    FieldEnd accountQueryFieldEnd = Mockito.mock(FieldEnd.class);
    when(wingsPersistence.createQuery(Account.class)).thenReturn(mockAccountQuery);
    when(mockAccountQuery.field(Mockito.anyString())).thenReturn(accountQueryFieldEnd);
    when(accountQueryFieldEnd.notEqual(Mockito.any())).thenReturn(mockAccountQuery);
    when(mockAccountQuery.count()).thenReturn(accountCount);
    when(wingsPersistence.createQuery(User.class)).thenReturn(mockUserQuery);
    when(mockUserQuery.count()).thenReturn(userCount);
  }

  private void fail(String message) {
    throw new RuntimeException(message);
  }
}
