/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.testframework.framework;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RequestSpecProvider {
  public RequestSpecification useDefaultSpec() {
    String host = System.getProperty("server.host", "https://localhost");
    String basePath = System.getProperty("server.base", "/api");
    String port = System.getProperty("server.port", "9090");
    return createRequestSpec(host, port, basePath);
  }

  public RequestSpecification useCISpec() {
    String host = System.getProperty("server.host", "https://localhost");
    String basePath = System.getProperty("server.base", "");
    String port = System.getProperty("server.port", "7171");
    return createRequestSpec(host, port, basePath);
  }

  public RequestSpecification useSTOSpec() {
    String host = System.getProperty("server.host", "https://localhost");
    String basePath = System.getProperty("server.base", "");
    String port = System.getProperty("server.port", "7172");
    return createRequestSpec(host, port, basePath);
  }

  public RequestSpecification useDefaultSpecForCommandLibraryService() {
    String host = System.getProperty("commandlibrary.server.host", "https://localhost");
    String basePath = System.getProperty("commandlibrary.server.base", "/command-library-service");
    String port = System.getProperty("commandlibrary.server.port", "5050");

    return createRequestSpec(host, port, basePath);
  }

  private RequestSpecification createRequestSpec(String host, String port, String basePath) {
    RequestSpecBuilder requestSpecBuilder = new RequestSpecBuilder();
    requestSpecBuilder.setAccept(ContentType.JSON);
    requestSpecBuilder.setContentType(ContentType.JSON);
    requestSpecBuilder.setBaseUri(host);
    requestSpecBuilder.setBasePath(basePath);
    if (!port.equals("0000")) {
      requestSpecBuilder.setPort(Integer.parseInt(port));
    }
    log.info("Querying environment : " + host + ":" + port + basePath);
    if (port.equals("0000")) {
      log.info(
          "This querying environment would use the default port for the service. This option is good to be used in non local envs such as QA");
    }
    return requestSpecBuilder.build();
  }

  public RequestSpecification useEmailSpec() {
    RequestSpecBuilder requestSpecBuilder = new RequestSpecBuilder();
    requestSpecBuilder.setAccept(ContentType.JSON);
    requestSpecBuilder.setContentType(ContentType.JSON);
    requestSpecBuilder.setBaseUri("https://api.guerrillamail.com");
    requestSpecBuilder.setBasePath("/ajax.php");
    RestAssured.useRelaxedHTTPSValidation();
    return requestSpecBuilder.build();
  }

  public RequestSpecification useMailinatorSpec(String decryptedToken) {
    RequestSpecBuilder requestSpecBuilder = new RequestSpecBuilder();
    requestSpecBuilder.setAccept(ContentType.JSON);
    requestSpecBuilder.setContentType(ContentType.JSON);
    requestSpecBuilder.setBaseUri("https://api.mailinator.com");
    requestSpecBuilder.setBasePath("/api");
    requestSpecBuilder.addQueryParam("token", decryptedToken);
    requestSpecBuilder.addQueryParam("private_domain", "true");
    RestAssured.useRelaxedHTTPSValidation();
    return requestSpecBuilder.build();
  }

  public RequestSpecification useGitSpec(String repoName) {
    RequestSpecBuilder requestSpecBuilder = new RequestSpecBuilder();
    requestSpecBuilder.setAccept(ContentType.JSON);
    requestSpecBuilder.setContentType(ContentType.JSON);
    requestSpecBuilder.setBaseUri("https://api.github.com/repos/wings-software");
    requestSpecBuilder.setBasePath("/" + repoName + "/contents/Setup/Applications");
    RestAssured.useRelaxedHTTPSValidation();
    return requestSpecBuilder.build();
  }

  public RequestSpecification useMockSpec() {
    String host = System.getProperty("server.host", "https://localhost");
    String basePath = System.getProperty("server.base", "");
    String port = System.getProperty("server.port", "8988");
    RestAssured.useRelaxedHTTPSValidation();
    return createRequestSpec(host, port, basePath);
  }
}
