/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.testframework.framework;

import static io.restassured.RestAssured.given;
import static org.apache.commons.codec.binary.Base64.encodeBase64String;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.exception.UnexpectedException;
import io.harness.rest.RestResponse;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;
import io.harness.testframework.framework.matchers.LoginMatcher;

import software.wings.beans.LoginRequest;
import software.wings.beans.User;

import io.restassured.specification.RequestSpecification;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.core.GenericType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Setup {
  private static RequestSpecProvider rqProvider = new RequestSpecProvider();

  private static ScmSecret instScmSecret = new ScmSecret();

  public static RequestSpecification portal() {
    return given().spec(rqProvider.useDefaultSpec());
  }
  public static RequestSpecification commandLibraryService() {
    return given().spec(rqProvider.useDefaultSpecForCommandLibraryService());
  }
  public static RequestSpecification email() {
    return given().spec(rqProvider.useEmailSpec());
  }
  public static RequestSpecification mailinator() {
    String secret = instScmSecret.decryptToString(new SecretName("mailinator_paid_api_key"));
    return given().spec(rqProvider.useMailinatorSpec(secret));
  }
  public static RequestSpecification ci() {
    return given().spec(rqProvider.useCISpec());
  }

  public static RequestSpecification sto() {
    return given().spec(rqProvider.useSTOSpec());
  }

  public static RequestSpecification mock() {
    return given().spec(rqProvider.useMockSpec());
  }

  public static User loginUser(String email, String password) {
    String basicAuthValue =
        "Basic " + encodeBase64String(String.format("%s:%s", email, password).getBytes(StandardCharsets.UTF_8));
    LoginRequest loginRequest = LoginRequest.builder().authorization(basicAuthValue).build();
    GenericType<RestResponse<User>> genericType = new GenericType<RestResponse<User>>() {};
    RestResponse<User> userRestResponse =
        Setup.portal().body(loginRequest).post("/users/login").as(genericType.getType());
    if (userRestResponse.getResource() == null) {
      throw new UnexpectedException(String.valueOf(userRestResponse.getResponseMessages()));
    }
    return userRestResponse.getResource();
  }

  public static String getAuthToken(String email, String password) {
    return loginUser(email, password).getToken();
  }

  public static String getAuthTokenForAccount(String accountId, String email, String password) {
    String basicAuthValue =
        "Basic " + encodeBase64String(String.format("%s:%s", email, password).getBytes(StandardCharsets.UTF_8));
    LoginRequest loginRequest = LoginRequest.builder().authorization(basicAuthValue).build();
    GenericType<RestResponse<User>> genericType = new GenericType<RestResponse<User>>() {};
    RestResponse<User> userRestResponse =
        Setup.portal().queryParam("accountId", accountId).post("/users/login", loginRequest).as(genericType.getType());
    assertThat(userRestResponse).isNotNull();
    User user = userRestResponse.getResource();
    assertThat(user).isNotNull();
    return user.getToken();
  }

  public static int signOut(String userId, String bearerToken) {
    return Setup.portal()
        .header("Authorization", "Bearer " + bearerToken)
        .post("/users/" + userId + "/logout")
        .getStatusCode();
  }

  public static User retryLogin(String userName, String password) {
    Retry retry = new Retry(5, 5000);
    return (User) retry.executeWithRetry(() -> loginUser(userName, password), new LoginMatcher(), null);
  }

  // TODO: Need to update and use this method
  public static RequestSpecification git(String repoName) {
    return given().spec(rqProvider.useGitSpec(repoName));
  }
}
