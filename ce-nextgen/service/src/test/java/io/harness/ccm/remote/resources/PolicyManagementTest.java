package io.harness.ccm.remote.resources;

import static io.harness.rule.OwnerRule.SAHIBA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.remote.resources.policies.*;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PolicyManagementTest extends CategoryTest {
  private PolicyStoreService policyStoreService = mock(PolicyStoreService.class);
  private CCMRbacHelper rbacHelper = mock(CCMRbacHelper.class);

  private final String ACCOUNT_ID = "ACCOUNT_ID";
  private final String UNIQUE_ID = "UNIQUE_ID";
  private final String NAME = "NAME";
  private final String RESOURCE = "RESOURCE";
  private final String POLICY = "POLICY";
  private final List<String> TAGS = Arrays.asList(new String[] {"TAGS"});
  private final String ERROR = "ERROR";
  private final String DESCRIPTION = "DESCRIPTION";
  private final String ORG_PARAM_MESSAGE = "ORG_PARAM_MESSAGE";
  private final String PROJECT_PARAM_MESSAGE = "PROJECT_PARAM_MESSAGE";
  private List<PolicyStore> Policies = new ArrayList<>();
  private PolicyStore policyStore;
  private QueryFeild queryFeild;
  private PolicyStore createPolicyDTO;
  private PolicyManagement policymanagement;
  private ListDTO listDTO;

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    policyStore = PolicyStore.builder()
                      .uuid(UNIQUE_ID)
                      .accountId(ACCOUNT_ID)
                      .name(NAME)
                      .resource(RESOURCE)
                      .description(DESCRIPTION)
                      .policyYaml(POLICY)
                      .tags(TAGS)
                      .orgIdentifier(ORG_PARAM_MESSAGE)
                      .projectIdentifier(PROJECT_PARAM_MESSAGE)
                      .build();
    policymanagement = new PolicyManagement(policyStoreService, rbacHelper);
    createPolicyDTO = policyStore.toDTO();
    Policies.add(policyStore);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testCreatePolicy() {
    policymanagement.create(ACCOUNT_ID, CreatePolicyDTO.builder().policyStore(policyStore).build());
    verify(policyStoreService).save(policyStore);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testUpadtePolicy() {
    ResponseDTO<PolicyStore> res =
        policymanagement.updatePolicy(ACCOUNT_ID, CreatePolicyDTO.builder().policyStore(policyStore).build());
    assertThat(res.getData()).isEqualTo(policyStore);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void deletePolicy() {
    policymanagement.delete(ACCOUNT_ID, UNIQUE_ID);
    verify(policyStoreService).delete(ACCOUNT_ID, UNIQUE_ID);
  }
}
