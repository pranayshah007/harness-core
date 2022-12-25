/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.az;

import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AuthCommandTest extends CategoryTest {
  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void servicePrincipalWithPasswordAuthTest() {
    Az client = Az.client(null);
    char[] password = "password".toCharArray();
    AuthCommand authCommand =
        client.auth().authType(AuthType.servicePrincipal).clientId("APP_ID").password(password).tenantId("TENANT_ID");

    assertThat(authCommand.command())
        .isEqualTo("az login --service-principal -u APP_ID -p password --tenant TENANT_ID");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void servicePrincipalWithCertAuthTest() {
    Az client = Az.client(null);
    byte[] cert = "cert".getBytes(StandardCharsets.UTF_8);
    AuthCommand authCommand =
        client.auth().authType(AuthType.servicePrincipal).clientId("APP_ID").cert(cert).tenantId("TENANT_ID");

    assertThat(authCommand.command()).isEqualTo("az login --service-principal -u APP_ID -p cert --tenant TENANT_ID");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void systemIdentityAuthTest() {
    Az client = Az.client(null);
    AuthCommand authCommand = client.auth().authType(AuthType.identity);

    assertThat(authCommand.command()).isEqualTo("az login --identity");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void userIdentityAuthTest() {
    Az client = Az.client(null);
    AuthCommand authCommand = client.auth().authType(AuthType.identity).username("CLIENT_ID");

    assertThat(authCommand.command()).isEqualTo("az login --identity --username CLIENT_ID");
  }
}
