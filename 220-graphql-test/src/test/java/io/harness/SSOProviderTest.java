/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.DEEPAK;

import static software.wings.graphql.schema.type.aggregation.ssoProvider.QLSSOType.LDAP;
import static software.wings.graphql.schema.type.aggregation.ssoProvider.QLSSOType.SAML;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.category.layer.GraphQLTests;
import io.harness.generator.AccountGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.rule.Owner;
import io.harness.testframework.graphql.QLTestObject;

import software.wings.beans.Account;
import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupSettings;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapUserSettings;
import software.wings.beans.sso.SamlSettings;
import software.wings.graphql.schema.type.QLSSOProvider.QLSSOProviderKeys;
import software.wings.graphql.schema.type.QLSSOProviderConnection;
import software.wings.service.intfc.SSOSettingService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class SSOProviderTest extends GraphQLTest {
  @Inject SSOSettingService ssoSettingService;
  private SamlSettings samlSettings;
  private LdapSettings ldapSettings;
  @Inject private OwnerManager ownerManager;
  private String accountId;
  private Account account;
  @Inject AccountGenerator accountGenerator;

  @Before
  public void setup() {
    final Randomizer.Seed seed = new Randomizer.Seed(0);
    final OwnerManager.Owners owners = ownerManager.create();
    account = accountGenerator.ensurePredefined(seed, owners, AccountGenerator.Accounts.GENERIC_TEST);
    accountId = account.getUuid();
    samlSettings = SamlSettings.builder()
                       .metaDataFile("TestMetaDataFile")
                       .url("TestURL")
                       .accountId(accountId)
                       .displayName("Okta")
                       .origin("TestOrigin")
                       .build();

    samlSettings = ssoSettingService.saveSamlSettings(samlSettings, false, false);
    ldapSettings = createLDAOSSOProvider();
  }

  public LdapSettings createLDAOSSOProvider() {
    LdapConnectionSettings connectionSettings = new LdapConnectionSettings();
    connectionSettings.setBindDN("testBindDN");
    connectionSettings.setBindPassword("testBindPassword");
    LdapUserSettings userSettings = new LdapUserSettings();
    userSettings.setBaseDN("testBaseDN");
    List<LdapUserSettings> userSettingsList = new ArrayList<>();
    userSettingsList.add(userSettings);
    LdapGroupSettings groupSettings = new LdapGroupSettings();
    groupSettings.setBaseDN("testBaseDN");
    ldapSettings =
        new LdapSettings("testSettings", accountId, connectionSettings, userSettingsList, Arrays.asList(groupSettings));
    return ssoSettingService.createLdapSettings(ldapSettings);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testGetLdapSetting() {
    String query = $GQL(/*
        query{
               ssoProvider(ssoProviderId:"%s"){
                    id
                    name
                    ssoType
                }
            }*/
        ldapSettings.getUuid());
    final QLTestObject qlTestObject = qlExecute(query, accountId);
    assertThat(qlTestObject.get(QLSSOProviderKeys.id)).isEqualTo(ldapSettings.getUuid());
    assertThat(qlTestObject.get(QLSSOProviderKeys.name)).isEqualTo(ldapSettings.getDisplayName());
    assertThat(qlTestObject.get(QLSSOProviderKeys.ssoType)).isEqualTo("LDAP");
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testGetSamlSetting() {
    String query = $GQL(/*
      query{
             ssoProvider(ssoProviderId:"%s"){
                  id
                  name
                  ssoType
              }
          }*/
        samlSettings.getUuid());
    final QLTestObject qlTestObject = qlExecute(query, accountId);
    assertThat(qlTestObject.get(QLSSOProviderKeys.id)).isEqualTo(samlSettings.getUuid());
    assertThat(qlTestObject.get(QLSSOProviderKeys.name)).isEqualTo(samlSettings.getDisplayName());
    assertThat(qlTestObject.get(QLSSOProviderKeys.ssoType)).isEqualTo("SAML");
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testListSSOSetting() {
    String query =
        $GQL(/*
query{
ssoProviders(limit:3){
  nodes{
     id
     name
     ssoType
  }
 }
}*/);
    QLSSOProviderConnection userGroupConnection = qlExecute(QLSSOProviderConnection.class, query, accountId);
    assertThat(userGroupConnection.getNodes().size()).isEqualTo(2);
    assertThat(userGroupConnection.getNodes().get(0).getId()).isEqualTo(samlSettings.getUuid());
    assertThat(userGroupConnection.getNodes().get(0).getName()).isEqualTo(samlSettings.getDisplayName());
    assertThat(userGroupConnection.getNodes().get(0).getSsoType()).isEqualTo(SAML);
    assertThat(userGroupConnection.getNodes().get(1).getId()).isEqualTo(ldapSettings.getUuid());
    assertThat(userGroupConnection.getNodes().get(1).getName()).isEqualTo(ldapSettings.getDisplayName());
    assertThat(userGroupConnection.getNodes().get(1).getSsoType()).isEqualTo(LDAP);
  }
}
