/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.ldap;

import static io.harness.rule.OwnerRule.AMAN;
import static io.harness.rule.OwnerRule.UNKNOWN;
import static io.harness.rule.OwnerRule.VIKAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupSettings;
import software.wings.beans.sso.LdapUserSettings;
import software.wings.helpers.ext.ldap.LdapResponse;
import software.wings.helpers.ext.ldap.LdapResponse.Status;
import software.wings.helpers.ext.ldap.LdapSearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.ldaptive.Connection;
import org.ldaptive.DefaultConnectionFactory;
import org.ldaptive.LdapEntry;
import org.ldaptive.LdapException;
import org.ldaptive.ResultCode;
import org.ldaptive.SearchResult;
import org.ldaptive.auth.AuthenticationResponse;
import org.ldaptive.auth.Authenticator;
import org.ldaptive.auth.SearchDnResolver;
import org.mockito.ArgumentMatchers;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;

/**
 * @author Swapnil
 */
@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({LdapSearch.class, LdapHelper.class})
@PowerMockIgnore({"javax.security.*", "javax.net.*"})
@OwnedBy(HarnessTeam.PL)
public class LdapHelperTest extends WingsBaseTest {
  private LdapSettings ldapSettings;
  private LdapHelper helper;
  private SearchResult searchResult;
  private LdapSearch.Builder searchBuilder;
  private LdapSearch search;

  private void mockLdapSearchBuilder(LdapSearch.Builder searchBuilder, LdapSearch search) {
    mockStatic(LdapSearch.class);
    when(LdapSearch.builder()).thenReturn(searchBuilder);
    when(searchBuilder.connectionFactory(any())).thenReturn(searchBuilder);
    when(searchBuilder.baseDN(any())).thenReturn(searchBuilder);
    when(searchBuilder.searchFilter(any())).thenReturn(searchBuilder);
    when(searchBuilder.limit(anyInt())).thenReturn(searchBuilder);
    when(searchBuilder.referralsEnabled(anyBoolean())).thenReturn(searchBuilder);
    when(searchBuilder.maxReferralHops(anyInt())).thenReturn(searchBuilder);
    when(searchBuilder.fallBackSearchFilter(anyString())).thenReturn(searchBuilder);
    when(searchBuilder.build()).thenReturn(search);
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    LdapConnectionSettings connectionSettings = new LdapConnectionSettings();
    connectionSettings.setBindDN("testBindDN");
    connectionSettings.setBindPassword("testBindPassword");
    LdapUserSettings userSettings = new LdapUserSettings();
    userSettings.setBaseDN("testBaseDN");
    List<LdapUserSettings> userSettingsList = new ArrayList<>();
    userSettingsList.add(userSettings);
    LdapGroupSettings groupSettings = new LdapGroupSettings();
    groupSettings.setBaseDN("testBaseDN");
    ldapSettings = LdapSettings.builder()
                       .accountId("testSettings")
                       .connectionSettings(connectionSettings)
                       .userSettingsList(userSettingsList)
                       .groupSettingsList(Arrays.asList(groupSettings))
                       .build();
    ldapSettings.setGroupSettings(groupSettings);
    ldapSettings.setUserSettings(userSettings);

    DefaultConnectionFactory cf = mock(DefaultConnectionFactory.class);
    Connection c = mock(Connection.class);
    when(cf.getConnection()).thenReturn(c);
    whenNew(DefaultConnectionFactory.class).withAnyArguments().thenReturn(cf);
    helper = new LdapHelper(ldapSettings.getConnectionSettings());

    searchResult = mock(SearchResult.class);
    searchBuilder = mock(LdapSearch.Builder.class);
    search = mock(LdapSearch.class);
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  @Ignore("Ignore because breaking TI tests")
  public void validateConnectionConfig() {
    assertThat(helper.validateConnectionConfig().getStatus()).isEqualTo(Status.SUCCESS);
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  @Ignore("Ignore because breaking TI tests")
  public void validateUserConfig() throws LdapException {
    mockLdapSearchBuilder(searchBuilder, search);
    when(search.execute()).thenReturn(searchResult);
    when(searchResult.size()).thenReturn(0);
    assertThat(helper.validateUserConfig(ldapSettings).getStatus()).isEqualTo(Status.FAILURE);

    when(searchResult.size()).thenReturn(1);
    assertThat(helper.validateUserConfig(ldapSettings).getStatus()).isEqualTo(Status.SUCCESS);

    LdapException ldapException = mock(LdapException.class);
    when(ldapException.getResultCode()).thenReturn(ResultCode.OPERATIONS_ERROR);
    when(search.execute()).thenThrow(ldapException);
    assertThat(helper.validateUserConfig(ldapSettings).getStatus()).isEqualTo(Status.FAILURE);
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  @Ignore("Ignore because breaking TI tests")
  public void validateGroupConfig() throws LdapException {
    mockLdapSearchBuilder(searchBuilder, search);
    when(search.execute(any())).thenReturn(searchResult);
    when(search.execute()).thenReturn(searchResult);
    when(searchResult.size()).thenReturn(0);
    assertThat(helper.validateGroupConfig(ldapSettings).getStatus()).isEqualTo(Status.FAILURE);

    when(searchResult.size()).thenReturn(1);
    assertThat(helper.validateGroupConfig(ldapSettings).getStatus()).isEqualTo(Status.SUCCESS);

    LdapException ldapException = mock(LdapException.class);
    when(ldapException.getResultCode()).thenReturn(ResultCode.OPERATIONS_ERROR);
    when(search.execute(ArgumentMatchers.any())).thenThrow(ldapException);
    assertThat(helper.validateGroupConfig(ldapSettings).getStatus()).isEqualTo(Status.FAILURE);
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  @Ignore("Ignore because breaking TI tests")
  public void populateGroupSize() throws LdapException {
    LdapEntry group = new LdapEntry("groupDN");
    SearchResult groups = new SearchResult(group);
    mockLdapSearchBuilder(searchBuilder, search);
    when(search.execute(ArgumentMatchers.any())).thenReturn(searchResult);
    when(searchResult.getEntries()).thenReturn(Collections.singletonList(group));
    when(searchResult.size()).thenReturn(1);
    helper.populateGroupSize(groups, ldapSettings);
    assertThat(group.getAttribute("groupSize").getStringValue()).isEqualTo("1");
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  @Ignore("Ignore because breaking TI tests")
  public void populateGroupSizeWithoutExtendedMatchingFilterShouldFailAndThenSucceed() throws LdapException {
    LdapEntry group = new LdapEntry("groupDN");
    SearchResult groups = new SearchResult(group);
    mockLdapSearchBuilder(searchBuilder, search);
    when(search.execute(ArgumentMatchers.any()))
        .thenThrow(new LdapException("Error", ResultCode.UNAVAILABLE_CRITICAL_EXTENSION))
        .thenReturn(searchResult);
    when(searchResult.getEntries()).thenReturn(Collections.singletonList(group));
    when(searchResult.size()).thenReturn(1);
    helper.populateGroupSize(groups, ldapSettings);
    assertThat(group.getAttribute("groupSize").getStringValue()).isEqualTo("1");
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  @Ignore("Ignore because breaking TI tests")
  public void getGroupByDn() throws LdapException {
    mockLdapSearchBuilder(searchBuilder, search);
    when(search.execute(any())).thenReturn(searchResult);
    when(search.execute()).thenReturn(searchResult);
    when(searchResult.size()).thenReturn(1);
    String oldBaseDN = ldapSettings.getGroupSettings().getBaseDN();
    helper.getGroupByDn(ldapSettings, "groupDN");
    assertThat(ldapSettings.getGroupSettings().getBaseDN()).isEqualTo(oldBaseDN);
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  @Ignore("Ignore because breaking TI tests")
  public void authenticate() throws Exception {
    mockLdapSearchBuilder(searchBuilder, search);
    when(search.execute(any())).thenReturn(searchResult);
    when(search.execute()).thenReturn(searchResult);
    when(searchResult.size()).thenReturn(1);
    SearchDnResolver searchDnResolver = mock(SearchDnResolver.class);
    when(search.getSearchDnResolver(anyString())).thenReturn(searchDnResolver);
    when(searchDnResolver.resolve(any())).thenReturn("");

    LdapResponse response = helper.authenticate(ldapSettings, "myemail@mycompany.com", "mypassword");
    assertThat(response.getStatus()).isEqualTo(Status.FAILURE);

    Authenticator authenticator = mock(Authenticator.class);
    whenNew(Authenticator.class).withAnyArguments().thenReturn(authenticator);
    when(searchDnResolver.resolve(any())).thenReturn("userDN");
    AuthenticationResponse authenticationResponse = mock(AuthenticationResponse.class);
    when(authenticationResponse.getResult()).thenReturn(true);
    when(authenticator.authenticate(any())).thenReturn(authenticationResponse);
    response = helper.authenticate(ldapSettings, "myemail@mycompany.com", "mypassword");
    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);

    when(authenticationResponse.getResult()).thenReturn(false);
    when(authenticationResponse.getResultCode()).thenReturn(ResultCode.INVALID_CREDENTIALS);
    response = helper.authenticate(ldapSettings, "myemail@mycompany.com", "mypassword");
    assertThat(response.getStatus()).isEqualTo(Status.FAILURE);

    LdapException ldapException = mock(LdapException.class);
    when(authenticator.authenticate(any())).thenThrow(ldapException);
    when(ldapException.getResultCode()).thenReturn(ResultCode.OPERATIONS_ERROR);
    response = helper.authenticate(ldapSettings, "myemail@mycompany.com", "mypassword");
    assertThat(response.getStatus()).isEqualTo(Status.FAILURE);
  }
}
