/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration.SSO.LDAP;

import static io.harness.rule.OwnerRule.AMAN;

import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.ACCOUNT_ID;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.ADMIN_HARNESS_ID;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.INVALID_TOKEN;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.LDAP_GROUP_DN_TO_LINK_TO_HARNESS_GROUP;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.adminAccountLdapPass;
import static software.wings.integration.SSO.LDAP.LdapTestHelper.buildLdapSettings;
import static software.wings.integration.SSO.LDAP.LdapUrlHelper.createEnableLdapAsDefaultLoginMechanismUrl;
import static software.wings.integration.SSO.LDAP.LdapUrlHelper.createEnableUserPassAsDefaultLoginMechanismUrl;
import static software.wings.integration.SSO.LDAP.LdapUrlHelper.createLinkGroupByUrl;
import static software.wings.integration.SSO.LDAP.LdapUrlHelper.createSearchGroupByNameUrl;
import static software.wings.integration.SSO.LDAP.LdapUrlHelper.createTestLdapConnSettingsURL;
import static software.wings.integration.SSO.LDAP.LdapUrlHelper.createTestLdapGroupSettingsURL;
import static software.wings.integration.SSO.LDAP.LdapUrlHelper.createTestLdapUserSettingsURL;
import static software.wings.integration.SSO.LDAP.LdapUrlHelper.createUnlinkGroupByUrl;
import static software.wings.integration.SSO.LDAP.LdapUrlHelper.createUploadLdapSettingsURL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.delegate.beans.ldap.LDAPTestAuthenticationRequest;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.beans.security.UserGroup;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapLinkGroupRequest;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.LdapTestResponse.Status;
import software.wings.integration.IntegrationTestBase;
import software.wings.security.authentication.AuthenticationUtils;
import software.wings.service.intfc.SSOService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.utils.WingsIntegrationTestConstants;

import com.google.inject.Inject;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Integration test for Ldap.
 */
@Slf4j
public class LdapIntegrationTest extends IntegrationTestBase implements WingsIntegrationTestConstants {
  @Inject private SSOService ssoService;
  @Inject private UserGroupService userGroupService;
  @Inject private UserService userService;
  @Inject private AuthenticationUtils authenticationUtil;

  private Client client;
  private LdapSettings ldapSettings;

  @Before
  public void setupLdap() throws KeyManagementException, NoSuchAlgorithmException {
    client = LdapTestHelper.getClient();
    loginAdminUser();
    deleteExistingLdapSettings();
    ldapSettings = buildLdapSettings();
    LdapIntegrationTestConstants.ACCOUNT_ID =
        authenticationUtil.getDefaultAccount(authenticationUtil.getUser(ADMIN_HARNESS_ID)).getUuid();
    LdapIntegrationTestConstants.USER_GROUP_ID =
        userGroupService.listByAccountId(ACCOUNT_ID, userService.getUserByEmail(ADMIN_HARNESS_ID), true)
            .get(0)
            .getUuid();
    userToken = INVALID_TOKEN;
  }

  @Test
  @Owner(developers = AMAN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: Aman to investigate and fix")
  public void testLdapConnectionSettings() {
    loginAdminUser();
    assertThat(userToken).isNotEqualTo(INVALID_TOKEN);
    createAndTestLdapSettings();

    // tests happy cases for ldap test api's
    ldapConnectionSettingsTest();
    ldapUserSettingsTest();
    ldapGroupSettingsTest();
  }

  @Test
  @Owner(developers = AMAN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: Aman to investigate and fix")
  public void testLdapLogin() {
    loginAdminUser();
    assertThat(userToken).isNotEqualTo(INVALID_TOKEN);
    createAndTestLdapSettings();

    // Authentication testing for Ldap
    enableLdapAsDefaultLoginMechanism();
    userToken = INVALID_TOKEN;
    loginUsingLdap();
    assertThat(userToken).isNotEqualTo(INVALID_TOKEN);
    revertDefaultLoginToUserPassword();
  }

  @Test
  @Owner(developers = AMAN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: Aman to investigate and fix")
  public void testLdapUserGroupLinking() {
    loginAdminUser();
    assertThat(userToken).isNotEqualTo(INVALID_TOKEN);
    createAndTestLdapSettings();

    // GroupLinking testing
    searchGroupByNameTest();
    linkLdapGroupToHarnessUserGroupTest();
    unlinkLdapGroupToHarnessUserGroupTest();
    revertDefaultLoginToUserPassword();
  }

  private void revertDefaultLoginToUserPassword() {
    String url = createEnableUserPassAsDefaultLoginMechanismUrl();
    log.info("Calling url: " + url);
    WebTarget target = client.target(url);
    Response response = getRequestBuilderWithAuthHeader(target).put(Entity.json(JsonUtils.asJson("")));
    assertThat(Response.Status.OK.getStatusCode()).isEqualTo(response.getStatus());
  }

  public void createAndTestLdapSettings() {
    String url = createUploadLdapSettingsURL();
    log.info("Calling url: " + url);
    WebTarget target = client.target(url);
    RestResponse<LdapSettings> ldapSettingsRestResponse = getRequestBuilderWithAuthHeader(target).post(
        Entity.json(JsonUtils.asJson(ldapSettings)), new GenericType<RestResponse<LdapSettings>>() {});
    LdapSettings resource = ldapSettingsRestResponse.getResource();
    assertThat(ACCOUNT_ID).isEqualTo(resource.getAccountId());
    assertThat(resource.getConnectionSettings()).isNotNull();
    assertThat(1).isEqualTo(resource.getUserSettingsList().size());
    assertThat(2).isEqualTo(resource.getGroupSettingsList().size());
  }

  public void ldapConnectionSettingsTest() {
    String url = createTestLdapConnSettingsURL();
    log.info("Calling url: " + url);
    WebTarget target = client.target(url);
    RestResponse<LdapTestResponse> ldapSettingsRestResponse = getRequestBuilderWithAuthHeader(target).post(
        Entity.json(JsonUtils.asJson(ldapSettings)), new GenericType<RestResponse<LdapTestResponse>>() {});
    LdapTestResponse resource = ldapSettingsRestResponse.getResource();
    assertThat(resource.getStatus()).isEqualTo(Status.SUCCESS);
  }

  public void ldapUserSettingsTest() {
    String url = createTestLdapUserSettingsURL();
    log.info("Calling url: " + url);
    WebTarget target = client.target(url);
    RestResponse<LdapTestResponse> ldapSettingsRestResponse = getRequestBuilderWithAuthHeader(target).post(
        Entity.json(JsonUtils.asJson(ldapSettings)), new GenericType<RestResponse<LdapTestResponse>>() {});
    LdapTestResponse resource = ldapSettingsRestResponse.getResource();
    assertThat(resource.getStatus()).isEqualTo(Status.SUCCESS);
  }

  public void ldapGroupSettingsTest() {
    String url = createTestLdapGroupSettingsURL();
    log.info("Calling url: " + url);
    WebTarget target = client.target(url);
    RestResponse<LdapTestResponse> ldapSettingsRestResponse = getRequestBuilderWithAuthHeader(target).post(
        Entity.json(JsonUtils.asJson(ldapSettings)), new GenericType<RestResponse<LdapTestResponse>>() {});
    LdapTestResponse resource = ldapSettingsRestResponse.getResource();
    assertThat(resource.getStatus()).isEqualTo(Status.SUCCESS);
  }

  public void enableLdapAsDefaultLoginMechanism() {
    String url = createEnableLdapAsDefaultLoginMechanismUrl();
    log.info("Calling url: " + url);
    WebTarget target = client.target(url);
    LDAPTestAuthenticationRequest ldapTestAuthenticationRequest = LdapTestHelper.getAuthenticationRequestObject();
    Response response =
        getRequestBuilderWithAuthHeader(target).put(Entity.json(JsonUtils.asJson(ldapTestAuthenticationRequest)));
    assertThat(Response.Status.OK.getStatusCode()).isEqualTo(response.getStatus());
  }

  public void searchGroupByNameTest() {
    LdapSettings ldapSettings = ssoService.getLdapSettings(ACCOUNT_ID);
    String url = createSearchGroupByNameUrl(ldapSettings.getUuid());
    log.info("Calling url: " + url);
    WebTarget target = client.target(url);
    RestResponse<Collection<LdapGroupResponse>> collectionRestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<Collection<LdapGroupResponse>>>() {});
    Collection<LdapGroupResponse> resource = collectionRestResponse.getResource();

    resource.forEach(
        groupResponse -> { assertThat(groupResponse.getName().toLowerCase().contains("admin")).isTrue(); });
    assertThat(resource.size() > 0).isTrue();
  }

  public void linkLdapGroupToHarnessUserGroupTest() {
    enableLdapAsDefaultLoginMechanism();
    loginUsingLdap();
    LdapSettings ldapSettings = ssoService.getLdapSettings(ACCOUNT_ID);
    String url = createLinkGroupByUrl(ldapSettings.getUuid());
    log.info("Calling url: " + url);
    WebTarget target = client.target(url);
    LdapLinkGroupRequest ldapLinkGroupRequest = LdapTestHelper.getLdapLinkGroupRequest();

    RestResponse<UserGroup> userGroupRestResponse = getRequestBuilderWithAuthHeader(target).put(
        Entity.json(JsonUtils.asJson(ldapLinkGroupRequest)), new GenericType<RestResponse<UserGroup>>() {});
    UserGroup userGroup = userGroupRestResponse.getResource();
    assertThat(userGroup.isSsoLinked()).isTrue();
    assertThat(LDAP_GROUP_DN_TO_LINK_TO_HARNESS_GROUP).isEqualTo(userGroup.getSsoGroupId());
  }

  public void unlinkLdapGroupToHarnessUserGroupTest() {
    String url = createUnlinkGroupByUrl();
    log.info("Calling url: " + url);
    WebTarget target = client.target(url);

    RestResponse<UserGroup> userGroupResponse =
        getRequestBuilderWithAuthHeader(target).put(Entity.json(""), new GenericType<RestResponse<UserGroup>>() {});
    UserGroup userGroup = userGroupResponse.getResource();
    assertThat(userGroup.isSsoLinked()).isFalse();
  }

  public void loginUsingLdap() {
    String loginUserToken = loginUser(ADMIN_HARNESS_ID, adminAccountLdapPass);
    assertThat(loginUserToken).isNotEqualTo(INVALID_TOKEN);
  }

  private void deleteExistingLdapSettings() {
    if (ssoService.getLdapSettings(ACCOUNT_ID) != null) {
      ssoService.deleteLdapSettings(ACCOUNT_ID);
    }
  }
}
