/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.signup.validator;

import static io.harness.annotations.dev.HarnessTeam.GTM;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.PASSWORD_STRENGTH_CHECK_FAILED;
import static io.harness.exception.WingsException.USER;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SignupException;
import io.harness.exception.UserAlreadyPresentException;
import io.harness.exception.WeakPasswordException;
import io.harness.ng.core.user.SignupAction;
import io.harness.remote.client.CGRestUtils;
import io.harness.signup.SignupDomainDenylistConfiguration;
import io.harness.signup.dto.SignupDTO;
import io.harness.user.remote.UserClient;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.commons.validator.routines.RegexValidator;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(GTM)
public class SignupValidator {
  private UserClient userClient;
  @Inject private SignupDomainDenylistConfiguration signupDomainDenylistConfiguration;

  private static final Pattern EMAIL_PATTERN = Pattern.compile("^\\s*?(.+)@(.+?)\\s*$");
  private final RegexValidator domainRegex = new RegexValidator(
      "^(?:\\p{Alnum}(?>[\\p{Alnum}-]{0,61}\\p{Alnum})?\\.)+(\\p{Alpha}(?>[\\p{Alnum}-]{0,61}\\p{Alnum})?)\\.?$");
  private static final List<String> whitelistedTopLevelDomains = ImmutableList.of("inc");

  private static final String COM = "com";
  private static final String EMAIL = "email";
  private static final String SIGNUP_DOMAIN_DENYLIST_NAME = "signupDomainDenylist.txt";

  public void validateSignup(SignupDTO dto) {
    validateEmail(dto.getEmail());
    validatePassword(dto.getPassword());
    validateIntent(dto);
    validateSignupAction(dto);
  }

  public void validateEmail(String email) {
    if (isBlank(email)) {
      throw new SignupException("Email cannot be empty.");
    }
    validateSignupDomain(email);
    String clonedEmail = email;

    String topLevelDomain = getTopLevelDomain(email);

    if (!EMPTY.equals(topLevelDomain) && whitelistedTopLevelDomains.contains(topLevelDomain)) {
      clonedEmail = replaceTopLevelDomain(topLevelDomain, clonedEmail);
    }

    final String emailAddress = email.trim();

    boolean userExists = CGRestUtils.getResponse(userClient.getUserByEmailId(email.toLowerCase())).isPresent();

    if (userExists) {
      throw new UserAlreadyPresentException("SignUp errored out. Please try again");
    }

    final String clonedEmailAddress = clonedEmail.trim();
    if (!EmailValidator.getInstance().isValid(clonedEmailAddress)) {
      throw new SignupException("This email is invalid. email=" + emailAddress);
    }
  }

  private void validateSignupDomain(String email) {
    String domainName = getDomain(email);
    Set<String> signupDomainDenylist = getSignupDomainDenylist();
    if (signupDomainDenylist.contains(domainName)) {
      String errorMessage = "Please use work email for signing up";
      log.error(errorMessage);
      throw new SignupException(errorMessage);
    }
  }

  private Set<String> getSignupDomainDenylist() {
    Set<String> signupDomainDenylist = new HashSet<>();
    try {
      byte[] fileContent = downloadFromGCS(SIGNUP_DOMAIN_DENYLIST_NAME);
      String[] patterns = new String(fileContent, StandardCharsets.UTF_8).split("\n");

      for (String pattern : patterns) {
        if (isNotEmpty(pattern)) {
          signupDomainDenylist.add(pattern.trim());
        }
      }
    } catch (Exception ex) {
      log.error("Error initializing domain denylist set for NG Signup", ex);
      return signupDomainDenylist;
    }

    log.info("Successfully initialized domain denylist. Set size {}", signupDomainDenylist.size());
    return signupDomainDenylist;
  }

  private byte[] downloadFromGCS(String fileName) {
    if (Objects.isNull(signupDomainDenylistConfiguration)) {
      log.error("Couldn't get GCS credentials from configuration");
      return null;
    }
    String projectId = signupDomainDenylistConfiguration.getProjectId();
    String bucketName = signupDomainDenylistConfiguration.getBucketName();
    String gcsCredsBase64 = signupDomainDenylistConfiguration.getGcsCreds();
    String objectName = fileName;

    if (isBlank(projectId) || isBlank(bucketName) || isBlank(gcsCredsBase64)) {
      log.error("Cannot connect to GCS as some configs are NULL. ProjectId: {}, BucketName: {}", projectId, bucketName);
      return null;
    }

    try {
      byte[] gcsCredsDecoded = Base64.getDecoder().decode(gcsCredsBase64);
      Credentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(gcsCredsDecoded));
      Storage storage =
          StorageOptions.newBuilder().setCredentials(credentials).setProjectId(projectId).build().getService();

      return storage.readAllBytes(bucketName, objectName);
    } catch (Exception e) {
      log.error("Exception occurred while fetching file {}", fileName, e);
      return null;
    }
  }

  private String getTopLevelDomain(String email) {
    String domain = getDomain(email);
    String[] groups = domainRegex.match(domain);
    return groups != null && groups.length > 0 ? groups[0] : EMPTY;
  }

  private String getDomain(String email) {
    Matcher emailMatcher = EMAIL_PATTERN.matcher(email);
    return emailMatcher.matches() ? emailMatcher.group(2) : EMPTY;
  }

  private String replaceTopLevelDomain(String topLevelDomain, String email) {
    StringBuilder emailStringBuilder = new StringBuilder(email);
    int lastIndex = email.lastIndexOf(topLevelDomain);
    return emailStringBuilder.replace(lastIndex, lastIndex + topLevelDomain.length(), COM).toString();
  }

  private void validatePassword(String password) {
    if (password == null || isBlank(password)) {
      throw new WeakPasswordException(
          "Password cannot be empty.", null, PASSWORD_STRENGTH_CHECK_FAILED, Level.ERROR, USER, null);
    }

    if (password.length() < 8) {
      throw new WeakPasswordException(
          "Password should at least be 8 characters.", null, PASSWORD_STRENGTH_CHECK_FAILED, Level.ERROR, USER, null);
    }

    if (password.length() > 64) {
      throw new WeakPasswordException("Password should be less than or equal to 64 characters.", null,
          PASSWORD_STRENGTH_CHECK_FAILED, Level.ERROR, USER, null);
    }
  }

  private void validateIntent(SignupDTO dto) {
    if (dto.getIntent() == null) {
      dto.setIntent("");
    } else {
      try {
        ModuleType.fromString(dto.getIntent());
      } catch (IllegalArgumentException e) {
        throw new InvalidRequestException("Invalid intent", e);
      }
    }
  }

  private void validateSignupAction(SignupDTO dto) {
    if (SignupAction.SUBSCRIBE.equals(dto.getSignupAction())) {
      if (isBlank(dto.getIntent())) {
        throw new SignupException("No module was specified for the trial. email=" + dto.getEmail());
      }

      if (dto.getBillingFrequency() == null) {
        throw new SignupException("No billing frequency was specified for the subscription. email=" + dto.getEmail());
      }
    }

    if (SignupAction.TRIAL.equals(dto.getSignupAction())) {
      if (isBlank(dto.getIntent())) {
        throw new SignupException("No module was specified for the trial. email=" + dto.getEmail());
      }

      if (dto.getEdition() == null) {
        throw new SignupException("No edition was specified for the trial. email=" + dto.getEmail());
      }
    }
  }
}
