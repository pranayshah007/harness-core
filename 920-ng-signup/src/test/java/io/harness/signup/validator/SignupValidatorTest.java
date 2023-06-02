/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.signup.validator;

import static io.harness.annotations.dev.HarnessTeam.GTM;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.NATHAN;
import static io.harness.rule.OwnerRule.ZHUO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SignupException;
import io.harness.exception.UserAlreadyPresentException;
import io.harness.exception.WeakPasswordException;
import io.harness.ng.core.user.UserInfo;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.signup.SignupDomainDenylistConfiguration;
import io.harness.signup.dto.SignupDTO;
import io.harness.user.remote.UserClient;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(GTM)
public class SignupValidatorTest extends CategoryTest {
  @InjectMocks SignupValidator signupValidator;
  @Mock UserClient userClient;
  @Mock SignupDomainDenylistConfiguration signupDomainDenylistConfiguration;

  @Before
  public void setUp() {
    initMocks(this);
  }

  @Test
  @Owner(developers = NATHAN)
  @Category(UnitTests.class)
  public void testValidSignup() throws IOException {
    String email = "test@google.com";

    Call<RestResponse<Optional<UserInfo>>> getUserCall = mock(Call.class);
    when(getUserCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.ofNullable(null))));
    when(userClient.getUserByEmailId(eq(email))).thenReturn(getUserCall);

    SignupDTO signupDTO = SignupDTO.builder().email(email).password("admin12345").build();

    try {
      signupValidator.validateSignup(signupDTO);
    } catch (Exception exception) {
      fail("The signup should pass because the email and password are valid");
    }
  }

  @Test
  @Owner(developers = NATHAN)
  @Category(UnitTests.class)
  public void testBlankEmail() throws IOException {
    String email = "";

    Call<RestResponse<Optional<UserInfo>>> getUserCall = mock(Call.class);
    when(getUserCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.ofNullable(null))));
    when(userClient.getUserByEmailId(eq(email))).thenReturn(getUserCall);

    SignupDTO signupDTO = SignupDTO.builder().email(email).password("admin12345").build();

    try {
      signupValidator.validateSignup(signupDTO);
      fail("The signup should fail because the email is invalid");
    } catch (Exception exception) {
      assertThat(exception.getClass()).isEqualTo(SignupException.class);
    }
  }

  @Test
  @Owner(developers = NATHAN)
  @Category(UnitTests.class)
  public void testDuplicateEmail() throws IOException {
    String email = "test@google.com";

    Call<RestResponse<Optional<UserInfo>>> getUserCall = mock(Call.class);
    when(getUserCall.execute())
        .thenReturn(Response.success(new RestResponse<>(Optional.of(UserInfo.builder().build()))));
    when(userClient.getUserByEmailId(eq(email))).thenReturn(getUserCall);

    SignupDTO signupDTO = SignupDTO.builder().email(email).password("admin12345").build();

    try {
      signupValidator.validateSignup(signupDTO);
      fail("The signup should fail because the email is a duplicate");
    } catch (Exception exception) {
      assertThat(exception.getClass()).isEqualTo(UserAlreadyPresentException.class);
    }
  }

  @Test
  @Owner(developers = NATHAN)
  @Category(UnitTests.class)
  public void testInvalidEmail() throws IOException {
    String email = "testgoogle.com";

    Call<RestResponse<Optional<UserInfo>>> getUserCall = mock(Call.class);
    when(getUserCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.ofNullable(null))));
    when(userClient.getUserByEmailId(eq(email))).thenReturn(getUserCall);

    SignupDTO signupDTO = SignupDTO.builder().email(email).password("admin12345").build();

    try {
      signupValidator.validateSignup(signupDTO);
      fail("The signup should fail because the email is invalid");
    } catch (Exception exception) {
      assertThat(exception.getClass()).isEqualTo(SignupException.class);
    }
  }

  @Test
  @Owner(developers = NATHAN)
  @Category(UnitTests.class)
  public void testWhitelistedDomain() throws IOException {
    String email = "test@google.inc";

    Call<RestResponse<Optional<UserInfo>>> getUserCall = mock(Call.class);
    when(getUserCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.ofNullable(null))));
    when(userClient.getUserByEmailId(eq(email))).thenReturn(getUserCall);

    SignupDTO signupDTO = SignupDTO.builder().email(email).password("admin12345").build();

    try {
      signupValidator.validateSignup(signupDTO);
    } catch (Exception exception) {
      fail("The signup should pass because inc is whitelisted");
    }
  }

  @Test
  @Owner(developers = NATHAN)
  @Category(UnitTests.class)
  public void testBlankPassword() throws IOException {
    String email = "test@google.com";

    Call<RestResponse<Optional<UserInfo>>> getUserCall = mock(Call.class);
    when(getUserCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.ofNullable(null))));
    when(userClient.getUserByEmailId(eq(email))).thenReturn(getUserCall);

    SignupDTO signupDTO = SignupDTO.builder().email(email).password("").build();

    try {
      signupValidator.validateSignup(signupDTO);
      fail("The signup should fail because the password is empty");
    } catch (Exception exception) {
      assertThat(exception.getClass()).isEqualTo(WeakPasswordException.class);
    }
  }

  @Test
  @Owner(developers = NATHAN)
  @Category(UnitTests.class)
  public void testShortPassword() throws IOException {
    String email = "test@google.com";

    Call<RestResponse<Optional<UserInfo>>> getUserCall = mock(Call.class);
    when(getUserCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.ofNullable(null))));
    when(userClient.getUserByEmailId(eq(email))).thenReturn(getUserCall);

    SignupDTO signupDTO = SignupDTO.builder().email(email).password("1").build();

    try {
      signupValidator.validateSignup(signupDTO);
      fail("The signup should fail because the password is less than 8 characters");
    } catch (Exception exception) {
      assertThat(exception.getClass()).isEqualTo(WeakPasswordException.class);
    }
  }

  @Test
  @Owner(developers = NATHAN)
  @Category(UnitTests.class)
  public void testLongPassword() throws IOException {
    String email = "test@google.com";

    Call<RestResponse<Optional<UserInfo>>> getUserCall = mock(Call.class);
    when(getUserCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.ofNullable(null))));
    when(userClient.getUserByEmailId(eq(email))).thenReturn(getUserCall);

    SignupDTO signupDTO = SignupDTO.builder()
                              .email(email)
                              .password("0123456789012345678901234567890123456789012345678901234567890123456789")
                              .build();

    try {
      signupValidator.validateSignup(signupDTO);
      fail("The signup should fail because the password is greater than 64 characters");
    } catch (Exception exception) {
      assertThat(exception.getClass()).isEqualTo(WeakPasswordException.class);
    }
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testInvalidIntent() throws IOException {
    String email = "test@google.com";

    Call<RestResponse<Optional<UserInfo>>> getUserCall = mock(Call.class);
    when(getUserCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.ofNullable(null))));
    when(userClient.getUserByEmailId(eq(email))).thenReturn(getUserCall);

    SignupDTO signupDTO = SignupDTO.builder().email(email).password("admin12345").intent("A").build();

    signupValidator.validateSignup(signupDTO);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetSignupDomainDenylist_withWrongGCSCredentials() {
    when(signupDomainDenylistConfiguration.getProjectId()).thenReturn("projectId");
    when(signupDomainDenylistConfiguration.getBucketName()).thenReturn("bucketId");
    when(signupDomainDenylistConfiguration.getGcsCreds()).thenReturn("gcsCreds");

    Set<String> signupDomainDenylist = signupValidator.getSignupDomainDenylist();
    assertThat(signupDomainDenylist).isEmpty();
  }
}
