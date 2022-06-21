package io.harness.ng.authenticationsettings;

import static io.harness.rule.OwnerRule.PRATEEK;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ldap.LdapGroupSearchTaskResponse;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.ng.ldap.search.NGLdapSearchService;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapGroupResponse;

import java.util.Collection;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PL)
@RunWith(MockitoJUnitRunner.class)
public class NGLdapSearchServiceTest extends CategoryTest {
  TaskSetupAbstractionHelper taskSetupAbstractionHelper = mock(TaskSetupAbstractionHelper.class);
  DelegateGrpcClientWrapper delegateGrpcClientWrapper = mock(DelegateGrpcClientWrapper.class);

  @Spy @InjectMocks private NGLdapSearchService ngLdapSearchService;

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSearchGroupsByName() {
    final String accountId = "testAccountId";
    String groupNameQuery = "grpName";
    LdapGroupResponse response = LdapGroupResponse.builder()
                                     .name(groupNameQuery)
                                     .description("desc")
                                     .dn("uid=ldap_user1,ou=Users,dc=jumpcloud,dc=com")
                                     .totalMembers(4)
                                     .build();
    Collection<LdapGroupResponse> matchedGroups = Collections.singletonList(response);

    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(LdapGroupSearchTaskResponse.builder().ldapListGroupsResponses(matchedGroups).build());

    Collection<LdapGroupResponse> ldapGroupResponse = ngLdapSearchService.searchGroupsByName(
        LdapSettings.builder().accountId(accountId).build(), EncryptedDataDetail.builder().build(), groupNameQuery);

    assertNotNull(ldapGroupResponse);
    assertFalse(ldapGroupResponse.isEmpty());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSearchGroupsByNameNoMatchingGroups() {
    final String accountId = "testAccountId";
    String groupNameQuery = "grpName";

    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(LdapGroupSearchTaskResponse.builder().ldapListGroupsResponses(Collections.emptyList()).build());

    Collection<LdapGroupResponse> ldapGroupResponse = ngLdapSearchService.searchGroupsByName(
        LdapSettings.builder().accountId(accountId).build(), EncryptedDataDetail.builder().build(), groupNameQuery);

    assertNotNull(ldapGroupResponse);
    assertTrue(ldapGroupResponse.isEmpty());
  }
}
