/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.sso;

import static io.harness.rule.OwnerRule.NATARAJA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.rule.Owner;
import io.harness.testframework.framework.utils.SSOUtils;
import io.harness.testframework.restutils.SSORestUtils;

import software.wings.beans.sso.LdapSettings;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class SSOCRUDTest extends AbstractFunctionalTest {
  @Test
  @Owner(developers = NATARAJA)
  @Category(FunctionalTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void testLDAPCRUD() {
    log.info("Starting the LDAP test");
    log.info("Creating LDAP SSO Setting");
    LdapSettings ldapSettings = SSOUtils.createDefaultLdapSettings(getAccount().getUuid());
    assertThat(SSORestUtils.addLdapSettings(getAccount().getUuid(), bearerToken, ldapSettings) == HttpStatus.SC_OK)
        .isTrue();
    Object ssoConfig = SSORestUtils.getAccessManagementSettings(getAccount().getUuid(), bearerToken);
    assertThat(ssoConfig).isNotNull();
    String ldapId = SSOUtils.getLdapId(ssoConfig);
    assertThat(StringUtils.isNotBlank(ldapId)).isTrue();
    assertThat(SSORestUtils.deleteLDAPSettings(getAccount().getUuid(), bearerToken) == HttpStatus.SC_OK).isTrue();
    log.info("LDAP CRUD test completed");
  }

  @Test()
  @Owner(developers = NATARAJA)
  @Category(FunctionalTests.class)
  @Ignore("Ignoring test for SAML as Delete passes in local but fails in pipeline execution")
  public void testSAMLCRUD() {
    log.info("Starting the SAML test");
    String filePath = System.getProperty("user.dir");
    filePath = filePath + "/"
        + "200-functional-test/src/test/resources/secrets/"
        + "SAML_SSO_Provider.xml";
    assertThat(SSORestUtils.addSAMLSettings(getAccount().getUuid(), bearerToken, "SAML", filePath) == HttpStatus.SC_OK)
        .isTrue();
    Object ssoConfig = SSORestUtils.getAccessManagementSettings(getAccount().getUuid(), bearerToken);
    assertThat(ssoConfig).isNotNull();
    assertThat(SSORestUtils.deleSAMLSettings(getAccount().getUuid(), bearerToken) == HttpStatus.SC_OK).isTrue();
    log.info("Done");
  }
}
