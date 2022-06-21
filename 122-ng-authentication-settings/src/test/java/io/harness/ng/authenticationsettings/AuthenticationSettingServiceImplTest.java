/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.authenticationsettings;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ldap.LdapGroupSearchTaskResponse;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataDetail;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.authenticationsettings.impl.AuthenticationSettingsServiceImpl;
import io.harness.ng.authenticationsettings.remote.AuthSettingsManagerClient;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.ldap.search.NGLdapSearchService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import org.mockito.Spy;
import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.SamlSettings;
import software.wings.security.authentication.SSOConfig;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class AuthenticationSettingServiceImplTest extends CategoryTest {
  @Mock private AuthSettingsManagerClient managerClient;
  @Mock private UserGroupService userGroupService;
  @Spy
  @InjectMocks private NGLdapSearchService ngLdapSearchService;
  @Inject @InjectMocks AuthenticationSettingsServiceImpl authenticationSettingsServiceImpl;

  @Mock private TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  private SamlSettings samlSettings;
  private LdapSettingsWithEncryptedDataDetail ldapSettingsWithEncryptedDataDetail;
  private static final String ACCOUNT_ID = "ACCOUNT_ID";

  @Before
  public void setup() {
    initMocks(this);
    samlSettings = SamlSettings.builder().accountId(ACCOUNT_ID).build();
    ldapSettingsWithEncryptedDataDetail = LdapSettingsWithEncryptedDataDetail.builder()
                                              .ldapSettings(LdapSettings.builder().build())
                                              .encryptedDataDetail(EncryptedDataDetail.builder().build())
                                              .build();
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteSamlMetadata() throws IOException {
    Call<RestResponse<SamlSettings>> request = mock(Call.class);
    doReturn(request).when(managerClient).getSAMLMetadata(ACCOUNT_ID);
    RestResponse<SamlSettings> mockResponse = new RestResponse<>(samlSettings);
    doReturn(Response.success(mockResponse)).when(request).execute();
    List<String> userGroups = new ArrayList<>();
    doReturn(userGroups).when(userGroupService).getUserGroupsBySsoId(ACCOUNT_ID, samlSettings.getUuid());
    SSOConfig ssoConfig = SSOConfig.builder().accountId(ACCOUNT_ID).build();
    Call<RestResponse<SSOConfig>> config = mock(Call.class);
    doReturn(config).when(managerClient).deleteSAMLMetadata(ACCOUNT_ID);
    RestResponse<SSOConfig> mockConfig = new RestResponse<>(ssoConfig);
    doReturn(Response.success(mockConfig)).when(config).execute();
    SSOConfig expectedConfig = authenticationSettingsServiceImpl.deleteSAMLMetadata(ACCOUNT_ID);
    assertThat(expectedConfig.getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteSamlMetadata_InvalidSSO_throwsException() throws IOException {
    Call<RestResponse<SamlSettings>> request = mock(Call.class);
    doReturn(request).when(managerClient).getSAMLMetadata(ACCOUNT_ID);
    RestResponse<SamlSettings> mockResponse = new RestResponse<>(null);
    doReturn(Response.success(mockResponse)).when(request).execute();
    try {
      authenticationSettingsServiceImpl.deleteSAMLMetadata(ACCOUNT_ID);
      fail("Deleting SAML metadata should fail.");
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage()).isEqualTo("No Saml Metadata found for this account");
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteSamlMetadata_WithExistingUserGroupsLinked_throwsException() throws IOException {
    Call<RestResponse<SamlSettings>> request = mock(Call.class);
    doReturn(request).when(managerClient).getSAMLMetadata(ACCOUNT_ID);
    RestResponse<SamlSettings> mockResponse = new RestResponse<>(samlSettings);
    doReturn(Response.success(mockResponse)).when(request).execute();
    List<String> userGroups = Collections.singletonList("userGroup1");
    doReturn(userGroups).when(userGroupService).getUserGroupsBySsoId(ACCOUNT_ID, samlSettings.getUuid());
    try {
      authenticationSettingsServiceImpl.deleteSAMLMetadata(ACCOUNT_ID);
      fail("Deleting SAML metadata should fail.");
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage())
          .isEqualTo("Deleting Saml provider with linked user groups is not allowed. Unlink the user groups first");
    }
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSearchLdapGroups() throws IOException {
    Call<RestResponse<LdapSettingsWithEncryptedDataDetail>> request = mock(Call.class);
    doReturn(request).when(managerClient).getLdapSettingsWithEncryptedDataDetails(ACCOUNT_ID);
    RestResponse<LdapSettingsWithEncryptedDataDetail> mockResponse =
        new RestResponse<>(ldapSettingsWithEncryptedDataDetail);
    doReturn(Response.success(mockResponse)).when(request).execute();
    Collection<LdapGroupResponse> userGroups = new ArrayList<>();
    doReturn(userGroups).when(ngLdapSearchService).searchGroupsByName(any(), any(), any());
    Collection<LdapGroupResponse> resultUserGroups =
        authenticationSettingsServiceImpl.searchLdapGroupsByName(ACCOUNT_ID, "TestLdapID", "TestGroupName");
    assertNotNull(resultUserGroups);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSearchLdapGroupsEmptyListWhenLdapSettingsNotFound() {
    Call<RestResponse<LdapSettingsWithEncryptedDataDetail>> request = mock(Call.class);
    doReturn(null).when(managerClient).getLdapSettingsWithEncryptedDataDetails(ACCOUNT_ID);
    Collection<LdapGroupResponse> resultUserGroups =
        authenticationSettingsServiceImpl.searchLdapGroupsByName(ACCOUNT_ID, "TestLdapID", "TestGroupName");
    assertTrue(resultUserGroups.isEmpty());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSearchGroupsByName() {
    final String accountId = "testAccountId";
    String groupNameQuery = "grpName";
    LdapGroupResponse response = LdapGroupResponse.builder().name(groupNameQuery).description("desc").dn("uid=ldap_user1,ou=Users,dc=jumpcloud,dc=com").totalMembers(4).build();
    Collection<LdapGroupResponse> matchedGroups = Collections.singletonList(response);

    when(delegateGrpcClientWrapper.executeSyncTask(any()))
            .thenReturn(LdapGroupSearchTaskResponse.builder().ldapListGroupsResponses(matchedGroups).build());

    Collection<LdapGroupResponse> ldapGroupResponse = ngLdapSearchService.searchGroupsByName(
            LdapSettings.builder().accountId(accountId).build(), EncryptedDataDetail.builder().build(), groupNameQuery);

    assertNotNull(ldapGroupResponse);
    assertFalse(ldapGroupResponse.isEmpty());
  }
}
