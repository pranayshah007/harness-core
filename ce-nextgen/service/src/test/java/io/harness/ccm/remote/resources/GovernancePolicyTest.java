/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.rule.OwnerRule.SAHIBA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.remote.resources.governance.*;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GovernancePolicyTest extends CategoryTest {
  private PolicyService policyService = mock(PolicyService.class);
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
  private List<Policy> Policies = new ArrayList<>();
  private Policy policy;
  private PolicyRequest policyRequest;
  private Policy createPolicyDTO;
  private GovernanceResource policymanagement;
  private ListDTO listDTO;

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    policy = Policy.builder()
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
    policymanagement = new GovernanceResource(policyService, rbacHelper);
    createPolicyDTO = policy.toDTO();
    Policies.add(policy);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testCreatePolicy() {
    policymanagement.create(ACCOUNT_ID, CreatePolicyDTO.builder().policy(policy).build());
    verify(policyService).save(policy);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testUpadtePolicy() {
    ResponseDTO<Policy> res =
        policymanagement.updatePolicy(ACCOUNT_ID, CreatePolicyDTO.builder().policy(policy).build());
    assertThat(res.getData()).isEqualTo(policy);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void deletePolicy() {
    policymanagement.delete(ACCOUNT_ID, UNIQUE_ID);
    verify(policyService).delete(ACCOUNT_ID, UNIQUE_ID);
  }
}
