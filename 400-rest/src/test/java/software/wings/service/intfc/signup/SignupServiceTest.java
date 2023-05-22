/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.signup;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.AMAN;
import static io.harness.rule.OwnerRule.MEHUL;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.exception.SignupException;
import io.harness.exception.WeakPasswordException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.UserInvite.UserInviteBuilder;
import software.wings.service.intfc.SignupService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
@TargetModule(HarnessModule._950_NG_SIGNUP)
@OwnedBy(PL)
public class SignupServiceTest extends WingsBaseTest {
  public static final String EMAIL = "test@harness.io";
  @Inject SignupService signupService;
  @Inject UserService userService;

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testInvalidEmail() {
    try {
      signupService.checkIfEmailIsValid("abc@example.com");
    } catch (Exception e) {
      assertThat(e).isNull();
    }
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testValidEmailUsingWhitelist() {
    try {
      signupService.checkIfEmailIsValid("abcinc@self.inc");
    } catch (Exception e) {
      assertThat(e).isNull();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testInvalidEmailNegative() {
    try {
      signupService.checkIfEmailIsValid("example");
    } catch (Exception e) {
      assertThat(e).isNotNull();
    }
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testInvalidUserCheckShouldSucceed() {
    try {
      signupService.checkIfUserInviteIsValid(null, "abc@example.com");
      fail();
    } catch (io.harness.exception.SignupException se) {
      assertThat(se.getMessage()).isEqualTo(String.format("Can not process signup for email: %s", "abc@example.com"));
    }
    try {
      signupService.checkIfUserInviteIsValid(
          UserInviteBuilder.anUserInvite().withCompleted(true).build(), "abc@example.com");
      fail();
    } catch (io.harness.exception.SignupException se) {
      assertThat(se.getMessage()).isEqualTo("User invite has already been completed. Please login");
    }
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testGetEmailShouldSucceed() {
    String signupSecretToken = userService.createSignupSecretToken(EMAIL, 10);
    assertThat(signupService.getEmail(signupSecretToken)).isEqualTo(EMAIL);

    try {
      signupSecretToken = userService.createSignupSecretToken(EMAIL, -10);
      signupService.getEmail(signupSecretToken);
      fail();
    } catch (SignupException ex) {
      log.info("Test behaved as expected");
    }
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testValidatePasswordThrowsWeakPasswordException() {
    final String weakPassword = "abc";
    assertThatThrownBy(() -> signupService.validatePassword(weakPassword.toCharArray()))
        .isInstanceOf(WeakPasswordException.class);
    final String blankPassword = "    ";
    assertThatThrownBy(() -> signupService.validatePassword(blankPassword.toCharArray()))
        .isInstanceOf(WeakPasswordException.class);
  }

  private void fail() {
    throw new RuntimeException("Expected to fail before reaching this line");
  }
}
