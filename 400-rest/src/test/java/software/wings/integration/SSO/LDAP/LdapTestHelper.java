/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration.SSO.LDAP;

import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.ACCOUNT_ID;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.LDAP_GROUP_DN_TO_LINK_TO_HARNESS_GROUP;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.LDAP_GROUP_NAME;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.bindDn;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.bindDnPassword;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.bindHost;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.connectionPort;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.connectionTimeout;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.email;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.groupMembershipAttr;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.groupSettingBaseDn1;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.groupSettingBaseDn2;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.groupSettingCn;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.groupSettingDescriptionAttr;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.groupSettingSearchFilter;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.maxReferralHops;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.pass;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.referencedUserAttr;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.referralsEnabled;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.responseTimeout;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.sslEabled;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.userMembershipAttr;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.userSettingBaseDn1;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.userSettingCn;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.userSettingEmailAttr;
import static software.wings.integration.SSO.LDAP.LdapIntegrationTestConstants.userSettingSearchFilter;

import io.harness.delegate.beans.ldap.LDAPTestAuthenticationRequest;
import io.harness.serializer.JsonSubtypeResolver;

import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupSettings;
import software.wings.beans.sso.LdapLinkGroupRequest;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapUserSettings;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

public class LdapTestHelper {
  static LdapSettings buildLdapSettings() {
    LdapSettings ldapSettings;
    LdapConnectionSettings connectionSettings;
    LdapUserSettings ldapUserSettings;
    LdapGroupSettings ldapGroupSettings;
    LdapGroupSettings ldapGroupSettingsSecond;

    connectionSettings = new LdapConnectionSettings();
    connectionSettings.setBindDN(bindDn);
    connectionSettings.setBindPassword(bindDnPassword);
    connectionSettings.setHost(bindHost);
    connectionSettings.setPort(connectionPort);
    connectionSettings.setReferralsEnabled(referralsEnabled);
    connectionSettings.setMaxReferralHops(maxReferralHops);
    connectionSettings.setSslEnabled(sslEabled);
    connectionSettings.setConnectTimeout(connectionTimeout);
    connectionSettings.setResponseTimeout(responseTimeout);

    ldapUserSettings = new LdapUserSettings();
    ldapUserSettings.setBaseDN(userSettingBaseDn1);
    ldapUserSettings.setDisplayNameAttr(userSettingCn);
    ldapUserSettings.setEmailAttr(userSettingEmailAttr);
    ldapUserSettings.setSearchFilter(userSettingSearchFilter);
    ldapUserSettings.setGroupMembershipAttr(groupMembershipAttr);

    ldapGroupSettings = new LdapGroupSettings();
    ldapGroupSettings.setBaseDN(groupSettingBaseDn1);
    ldapGroupSettings.setSearchFilter(groupSettingSearchFilter);
    ldapGroupSettings.setNameAttr(groupSettingCn);
    ldapGroupSettings.setDescriptionAttr(groupSettingDescriptionAttr);
    ldapGroupSettings.setUserMembershipAttr(userMembershipAttr);
    ldapGroupSettings.setReferencedUserAttr(referencedUserAttr);

    ldapGroupSettings = new LdapGroupSettings();
    ldapGroupSettings.setBaseDN(groupSettingBaseDn1);
    ldapGroupSettings.setSearchFilter(groupSettingSearchFilter);
    ldapGroupSettings.setNameAttr(groupSettingCn);
    ldapGroupSettings.setDescriptionAttr(groupSettingDescriptionAttr);
    ldapGroupSettings.setUserMembershipAttr(userMembershipAttr);
    ldapGroupSettings.setReferencedUserAttr(referencedUserAttr);

    ldapGroupSettingsSecond = new LdapGroupSettings();
    ldapGroupSettingsSecond.setBaseDN(groupSettingBaseDn2);
    ldapGroupSettingsSecond.setSearchFilter(groupSettingSearchFilter);
    ldapGroupSettingsSecond.setNameAttr(groupSettingCn);
    ldapGroupSettingsSecond.setDescriptionAttr(groupSettingDescriptionAttr);
    ldapGroupSettingsSecond.setUserMembershipAttr(userMembershipAttr);
    ldapGroupSettingsSecond.setReferencedUserAttr(referencedUserAttr);

    ldapSettings = LdapSettings.builder()
                       .connectionSettings(connectionSettings)
                       .groupSettingsList(Arrays.asList(ldapGroupSettings, ldapGroupSettingsSecond))
                       .userSettingsList(Arrays.asList(ldapUserSettings))
                       .accountId(ACCOUNT_ID)
                       .displayName("LDAP")
                       .build();

    return ldapSettings;
  }

  public static Client getClient() throws KeyManagementException, NoSuchAlgorithmException {
    Client client;

    SSLContext sslcontext = SSLContext.getInstance("TLS");
    X509TrustManager x509TrustManager = new X509TrustManager() {
      @Override
      public void checkClientTrusted(X509Certificate[] arg0, String arg1) {}

      @Override
      public void checkServerTrusted(X509Certificate[] arg0, String arg1) {}

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
      }
    };
    sslcontext.init(null, new TrustManager[] {x509TrustManager}, new SecureRandom());

    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    objectMapper.setSubtypeResolver(new JsonSubtypeResolver(objectMapper.getSubtypeResolver()));

    JacksonJaxbJsonProvider jacksonProvider = new JacksonJaxbJsonProvider();
    jacksonProvider.setMapper(objectMapper);

    client = ClientBuilder.newBuilder()
                 .sslContext(sslcontext)
                 .hostnameVerifier((s1, s2) -> true)
                 .register(MultiPartFeature.class)
                 .register(jacksonProvider)
                 .build();
    return client;
  }

  public static LDAPTestAuthenticationRequest getAuthenticationRequestObject() {
    LDAPTestAuthenticationRequest ldapTestAuthenticationRequest =
        LDAPTestAuthenticationRequest.builder().email(email).password(pass).build();
    return ldapTestAuthenticationRequest;
  }

  public static LdapLinkGroupRequest getLdapLinkGroupRequest() {
    LdapLinkGroupRequest ldapLinkGroupRequest = new LdapLinkGroupRequest();
    ldapLinkGroupRequest.setLdapGroupDN(LDAP_GROUP_DN_TO_LINK_TO_HARNESS_GROUP);
    ldapLinkGroupRequest.setLdapGroupName(LDAP_GROUP_NAME);
    return ldapLinkGroupRequest;
  }

  protected Builder getRequestBuilderWithAuthHeader(WebTarget target, String userToken) {
    return target.request().header("Authorization", "Bearer " + userToken);
  }
}
