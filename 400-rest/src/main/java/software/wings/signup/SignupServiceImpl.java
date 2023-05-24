/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.signup;

import static io.harness.annotations.dev.HarnessModule._950_NG_SIGNUP;
import static io.harness.annotations.dev.HarnessTeam.GTM;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.eraro.ErrorCode.INVALID_EMAIL;
import static io.harness.eraro.ErrorCode.PASSWORD_STRENGTH_CHECK_FAILED;
import static io.harness.exception.WingsException.USER;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.Level;
import io.harness.exception.SignupException;
import io.harness.exception.WeakPasswordException;
import io.harness.exception.WingsException;

import software.wings.app.MainConfiguration;
import software.wings.beans.UserInvite;
import software.wings.beans.UserInvite.UserInviteKeys;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.persistence.mail.EmailData;
import software.wings.service.intfc.EmailNotificationService;
import software.wings.service.intfc.SignupService;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.commons.validator.routines.RegexValidator;
import org.apache.http.client.utils.URIBuilder;

@OwnedBy(GTM)
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
@Singleton
@TargetModule(_950_NG_SIGNUP)
public class SignupServiceImpl implements SignupService {
  @Inject EmailNotificationService emailNotificationService;
  @Inject MainConfiguration configuration;
  @Inject WingsPersistence wingsPersistence;
  @Inject BlackListedDomainChecker blackListedDomainChecker;
  @Inject MainConfiguration mainConfiguration;
  @Inject private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Inject PwnedPasswordChecker pwnedPasswordChecker;

  private static final String COM = "com";
  private static final String EMAIL = "email";
  private final RegexValidator domainRegex = new RegexValidator(
      "^(?:\\p{Alnum}(?>[\\p{Alnum}-]{0,61}\\p{Alnum})?\\.)+(\\p{Alpha}(?>[\\p{Alnum}-]{0,61}\\p{Alnum})?)\\.?$");
  private static final Pattern EMAIL_PATTERN = Pattern.compile("^\\s*?(.+)@(.+?)\\s*$");
  private static final String TRIAL_SIGNUP_COMPLETED_TEMPLATE_NAME = "trial_signup_completed";

  private static final List<String> whitelistedTopLevelDomains = ImmutableList.of("inc");

  private static final String CD_TRIAL_VERIFICATION_TEMPLATE = "trial_verification_cd";
  private static final String CE_TRIAL_VERIFICATION_TEMPLATE = "trial_verification_ce";
  private static final String CI_TRIAL_VERIFICATION_TEMPLATE = "trial_verification_ci";
  private static final String PLATFORM_TRIAL_VERIFICATION_TEMPLATE = "trial_verification_platform";

  private static final String TRIAL_SIGNUP_LINKEDIN_TEMPLATE_NAME = "trial_signup_linkedin";
  private static final String TRIAL_SIGNUP_COMPLETED_LINKEDIN_TEMPLATE_NAME = "trial_signup_completed_linkedin";

  private List<Character> illegalCharacters = Collections.unmodifiableList(Arrays.asList(
      '$', '&', ',', '/', ':', ';', '=', '?', '<', '>', '#', '{', '}', '|', '^', '~', '(', ')', ']', '`', '\'', '\"'));

  @Override
  public void sendTrialSignupCompletedEmail(UserInvite userInvite) {
    try {
      Map<String, String> templateModel = getTrialSignupCompletedTemplateModel(userInvite);
      sendEmail(userInvite, TRIAL_SIGNUP_COMPLETED_TEMPLATE_NAME, templateModel);
    } catch (URISyntaxException e) {
      log.error("Trial sign-up completed email couldn't be sent ", e);
    }
  }

  @Override
  public void sendLinkedInTrialSignupCompletedEmail(UserInvite userInvite, String generatedPassword) {
    try {
      Map<String, String> templateModel = getTrialSignupCompletedTemplateModel(userInvite);
      String moduleName = getModuleName(userInvite);
      templateModel.put("module", moduleName);
      templateModel.put("email", userInvite.getEmail());
      templateModel.put("password", generatedPassword);
      String logo = getLogo(userInvite);
      templateModel.put("logo", logo);
      sendEmail(userInvite, TRIAL_SIGNUP_COMPLETED_LINKEDIN_TEMPLATE_NAME, templateModel);
    } catch (URISyntaxException e) {
      log.error("Trial sign-up completed email couldn't be sent ", e);
    }
  }

  private Map<String, String> getTrialSignupCompletedTemplateModel(UserInvite userInvite) throws URISyntaxException {
    Map<String, String> model = new HashMap<>();
    String loginUrl = buildAbsoluteUrl("/login", userInvite);
    model.put("name", userInvite.getEmail());
    model.put("url", loginUrl);
    return model;
  }

  private String buildAbsoluteUrl(String fragment, UserInvite userInvite) throws URISyntaxException {
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(userInvite.getAccountId());
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setFragment(fragment);
    return uriBuilder.toString();
  }

  @Override
  public void sendEmail(UserInvite userInvite, String templateName, Map<String, String> templateModel) {
    List<String> toList = new ArrayList<>();
    toList.add(userInvite.getEmail());
    EmailData emailData =
        EmailData.builder().to(toList).templateName(templateName).templateModel(templateModel).build();

    emailData.setCc(Collections.emptyList());
    emailData.setRetries(2);
    emailData.setAccountId(userInvite.getAccountId());

    emailNotificationService.send(emailData);
  }

  @Override
  public UserInvite getUserInviteByEmail(String email) {
    UserInvite userInvite = null;
    if (isNotEmpty(email)) {
      userInvite = wingsPersistence.createQuery(UserInvite.class).filter(UserInviteKeys.email, email).get();
    }
    return userInvite;
  }

  @Override
  public void validateEmail(String email) {
    // Only validate if the email address is valid. Won't check if the email has been registered already.
    checkIfEmailIsValid(email);

    if (containsIllegalCharacters(email)) {
      throw new WingsException(GENERAL_ERROR, USER)
          .addParam("message", "The email used for trial registration contains illegal characters.");
    }
    blackListedDomainChecker.check(email);
  }

  @Override
  public void validateCluster() {
    if (!configuration.isTrialRegistrationAllowed()) {
      throw new SignupException("Signup not allowed in this cluster");
    }
  }

  private boolean containsIllegalCharacters(String email) {
    for (Character illegalChar : illegalCharacters) {
      if (email.indexOf(illegalChar) >= 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Added support for whitelisted domains because some top level domains are not supported by EmailValidator. If an
   * email's domain is in whitelist, then EmailValidator will be called with the masked email where top level domain
   * will be replaced by "com"
   * @param email
   */
  @Override
  public void checkIfEmailIsValid(String email) {
    String clonedEmail = email;

    String topLevelDomain = getTopLevelDomain(email);
    if (!EMPTY.equals(topLevelDomain) && whitelistedTopLevelDomains.contains(topLevelDomain)) {
      clonedEmail = replaceTopLevelDomain(topLevelDomain, clonedEmail);
    }
    if (isBlank(clonedEmail)) {
      log.error("Blank user email found");
      throw new WingsException(INVALID_EMAIL, USER).addParam(EMAIL, email);
    }

    final String clonedEmailAddress = clonedEmail.trim();
    final String emailAddress = email.trim();
    if (!EmailValidator.getInstance().isValid(clonedEmailAddress)) {
      log.error("Invalid user email with id {}", email);
      throw new WingsException(INVALID_EMAIL, USER).addParam(EMAIL, emailAddress);
    }
  }

  /**
   * Gets the top level domain for an email.
   * @param email
   * @return
   */
  private String getTopLevelDomain(String email) {
    Matcher emailMatcher = EMAIL_PATTERN.matcher(email);
    String domain = emailMatcher.matches() ? emailMatcher.group(2) : EMPTY;
    String[] groups = domainRegex.match(domain);
    return groups != null && groups.length > 0 ? groups[0] : EMPTY;
  }

  private String replaceTopLevelDomain(String topLevelDomain, String email) {
    StringBuilder emailStringBuilder = new StringBuilder(email);
    int lastIndex = email.lastIndexOf(topLevelDomain);
    return emailStringBuilder.replace(lastIndex, lastIndex + topLevelDomain.length(), COM).toString();
  }

  @Override
  public void sendLinkedInSignupVerificationEmail(UserInvite userInvite) {
    String jwtPasswordSecret = configuration.getPortal().getJwtPasswordSecret();
    try {
      String token = createSignupTokenFromSecret(jwtPasswordSecret, userInvite.getEmail(), 30);
      String resetPasswordUrl = getResetPasswordUrl(token, userInvite);
      Map<String, String> templateModel = new HashMap<>();
      templateModel.put("url", resetPasswordUrl);
      templateModel.put("name", userInvite.getName());
      sendLinkedInTrialVerificationEmail(userInvite, templateModel);

    } catch (URISyntaxException | UnsupportedEncodingException e) {
      log.error("Signup verification email couldn't be sent", e);
    }
  }

  private void sendLinkedInTrialVerificationEmail(UserInvite userInvite, Map<String, String> templateModel) {
    String templateName = getLinkedInTrialSignupTemplateName(userInvite);

    // for now no email should be sent for CI only flow
    if (templateName != null) {
      String moduleName = getModuleName(userInvite);
      templateModel.put("module", moduleName);
      String logo = getLogo(userInvite);
      templateModel.put("logo", logo);
      sendEmail(userInvite, templateName, templateModel);
    }
  }

  private String getModuleName(UserInvite userInvite) {
    List<String> freemiumProducts = userInvite.getFreemiumProducts();

    if (freemiumProducts == null || freemiumProducts.size() > 1) {
      return "Platform";
    }

    String singleProduct = freemiumProducts.get(0);

    switch (singleProduct) {
      case "CD - Continuous Delivery":
        return "Continuous Delivery";
      case "CE - Continuous Efficiency":
        return "Continuous Efficiency";
      case "CI - Continuous Integration":
        return "Continuous Integration";
      default:
        log.error("Unknown product: {}", singleProduct);
        return "Platform";
    }
  }

  private String getLogo(UserInvite userInvite) {
    List<String> freemiumProducts = userInvite.getFreemiumProducts();

    if (freemiumProducts == null || freemiumProducts.size() > 1) {
      return "Platform";
    }

    String singleProduct = freemiumProducts.get(0);

    switch (singleProduct) {
      case "CD - Continuous Delivery":
        return "cd_logo";
      case "CE - Continuous Efficiency":
        return "ce_logo";
      case "CI - Continuous Integration":
        return "ci_logo";
      default:
        log.error("Unknown product: {}", singleProduct);
        return "Platform";
    }
  }

  private String getLinkedInTrialSignupTemplateName(UserInvite userInvite) {
    List<String> freemiumProducts = userInvite.getFreemiumProducts();

    if (freemiumProducts == null || freemiumProducts.size() > 1) {
      return TRIAL_SIGNUP_LINKEDIN_TEMPLATE_NAME;
    }

    String singleProduct = freemiumProducts.get(0);

    if ("CI - Continuous Integration".equals(singleProduct)) {
      return null;
    } else {
      return TRIAL_SIGNUP_LINKEDIN_TEMPLATE_NAME;
    }
  }

  @Override
  public void sendTrialSignupVerificationEmail(UserInvite userInvite, Map<String, String> templateModel) {
    String templateName = getTrialSignupTemplateName(userInvite);

    // for now no email should be sent for CI only flow
    if (!templateName.equals(CI_TRIAL_VERIFICATION_TEMPLATE)) {
      sendEmail(userInvite, templateName, templateModel);
    }
  }

  private String getTrialSignupTemplateName(UserInvite userInvite) {
    List<String> freemiumProducts = userInvite.getFreemiumProducts();

    if (freemiumProducts == null || freemiumProducts.size() > 1) {
      return PLATFORM_TRIAL_VERIFICATION_TEMPLATE;
    }

    String singleProduct = freemiumProducts.get(0);

    switch (singleProduct) {
      case "CD - Continuous Delivery":
        return CD_TRIAL_VERIFICATION_TEMPLATE;
      case "CE - Continuous Efficiency":
        return CE_TRIAL_VERIFICATION_TEMPLATE;
      case "CI - Continuous Integration":
        return CI_TRIAL_VERIFICATION_TEMPLATE;
      default:
        log.error("Unknown product: {}", singleProduct);
        return PLATFORM_TRIAL_VERIFICATION_TEMPLATE;
    }
  }

  @Override
  public String createSignupTokenFromSecret(String jwtPasswordSecret, String email, int expireAfterDays)
      throws UnsupportedEncodingException {
    Algorithm algorithm = Algorithm.HMAC256(jwtPasswordSecret);
    return JWT.create()
        .withIssuer("Harness Inc")
        .withIssuedAt(new Date())
        .withExpiresAt(new Date(System.currentTimeMillis() + (long) expireAfterDays * 24 * 60 * 60 * 1000)) // 24 hrs
        .withClaim(EMAIL, email)
        .sign(algorithm);
  }

  private String getResetPasswordUrl(String token, UserInvite userInvite) throws URISyntaxException {
    // always the call should go to the free cluster because a trial account will be created.
    String mode = "?mode=signup";
    return buildAbsoluteUrl("/complete-signup/" + token + mode, userInvite);
  }

  @Override
  public void validatePassword(char[] password) {
    if (password == null || isBlank(String.valueOf(password))) {
      throw new WeakPasswordException(
          "Password cannot be empty.", null, PASSWORD_STRENGTH_CHECK_FAILED, Level.ERROR, USER, null);
    }

    if (password.length < 8) {
      throw new WeakPasswordException(
          "Password should at least be 8 characters.", null, PASSWORD_STRENGTH_CHECK_FAILED, Level.ERROR, USER, null);
    }

    if (password.length > 64) {
      throw new WeakPasswordException("Password should be less than or equal to 64 characters.", null,
          PASSWORD_STRENGTH_CHECK_FAILED, Level.ERROR, USER, null);
    }

    if (!mainConfiguration.isPwnedPasswordsAllowed()) {
      boolean isPasswordPwned = false;

      try {
        isPasswordPwned = pwnedPasswordChecker.checkIfPwned(password);
      } catch (Exception e) {
        log.error("Received exception while checking for pwned passwords. Logging and ignoring the check", e);
      }

      if (isPasswordPwned) {
        throw new WeakPasswordException(
            "The password you entered has been flagged as vulnerable. To ensure security of your account, please enter a different password.",
            null, PASSWORD_STRENGTH_CHECK_FAILED, Level.ERROR, USER, null);
      }
    }
  }

  @Override
  public String getEmail(String jwtToken) {
    try {
      Algorithm algorithm = Algorithm.HMAC256(mainConfiguration.getPortal().getJwtPasswordSecret());
      JWTVerifier verifier = JWT.require(algorithm).withIssuer("Harness Inc").build();
      verifier.verify(jwtToken);
      JWT decode = JWT.decode(jwtToken);
      return decode.getClaim(EMAIL).asString();
    } catch (UnsupportedEncodingException exception) {
      log.error("Could not decode token for signup: {}", jwtToken);
      throw new SignupException("Invalid signup token. Please signup again");
    } catch (JWTVerificationException exception) {
      log.error("Signup token {} has expired", jwtToken);
      throw new SignupException("Invalid signup token. Please signup again");
    }
  }

  @Override
  public void checkIfUserInviteIsValid(UserInvite userInvite, String email) {
    if (userInvite == null) {
      log.info("No invite found in db for for email: {}", email);
      throw new SignupException(String.format("Can not process signup for email: %s", email));
    } else if (userInvite.isCompleted()) {
      throw new SignupException("User invite has already been completed. Please login");
    }
  }
}
