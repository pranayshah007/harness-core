package io.harness.ldap.resource;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.PRATEEK;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataDetail;
import io.harness.ldap.service.NGLdapService;
import io.harness.ng.authenticationsettings.remote.AuthSettingsManagerClient;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapGroupResponse;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class NGLdapResourceImplTest extends CategoryTest {
  @Mock private AuthSettingsManagerClient managerClient;
  @Mock private NGLdapService ngLdapService;
  @Inject @InjectMocks NGLdapResourceImpl ngLdapResourceImpl;

  private LdapSettingsWithEncryptedDataDetail ldapSettingsWithEncryptedDataDetail;
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";

  @Before
  public void setup() {
    initMocks(this);
    ldapSettingsWithEncryptedDataDetail = LdapSettingsWithEncryptedDataDetail.builder()
                                              .ldapSettings(LdapSettings.builder().build())
                                              .encryptedDataDetail(EncryptedDataDetail.builder().build())
                                              .build();
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSearchLdapGroups() throws IOException {
    int totalMembers = 4;
    final String groupQueryStr = "testGroupName";
    Call<RestResponse<LdapSettingsWithEncryptedDataDetail>> request = mock(Call.class);
    doReturn(request).when(managerClient).getLdapSettingsWithEncryptedDataDetails(ACCOUNT_ID);
    RestResponse<LdapSettingsWithEncryptedDataDetail> mockResponse =
        new RestResponse<>(ldapSettingsWithEncryptedDataDetail);
    doReturn(Response.success(mockResponse)).when(request).execute();
    LdapGroupResponse groupResponse = LdapGroupResponse.builder()
                                          .name(groupQueryStr)
                                          .description("desc")
                                          .dn("uid=ldap_user1,ou=Users,dc=jumpcloud,dc=com")
                                          .totalMembers(totalMembers)
                                          .build();
    Collection<LdapGroupResponse> userGroups = Collections.singletonList(groupResponse);
    doReturn(userGroups).when(ngLdapService).searchLdapGroupsByName(any(), any(), any(), any(), any());

    RestResponse<Collection<LdapGroupResponse>> collectionRestResponse =
        ngLdapResourceImpl.searchLdapGroups("TestLdapID", ACCOUNT_ID, ORG_ID, PROJECT_ID, groupQueryStr);
    assertNotNull(collectionRestResponse);
    assertFalse(collectionRestResponse.getResource().isEmpty());
    assertThat(collectionRestResponse.getResource().size()).isEqualTo(1);
    assertThat(collectionRestResponse.getResource().iterator().next().getTotalMembers()).isEqualTo(totalMembers);
  }
}
