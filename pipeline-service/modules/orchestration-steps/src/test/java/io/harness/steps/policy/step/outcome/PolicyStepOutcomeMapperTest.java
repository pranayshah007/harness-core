/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.policy.step.outcome;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.opaclient.model.OpaPolicyEvaluationResponse;
import io.harness.opaclient.model.OpaPolicySetEvaluationResponse;
import io.harness.opaclient.model.PolicyData;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PolicyStepOutcomeMapperTest extends CategoryTest {
  String accountIdentifier = "accId";
  String orgIdentifier = "orgId";
  String projectIdentifier = "proId";
  String policyIdentifier = "myId";
  String policyName = "my Id";
  String policySetIdentifier = "myPSId";
  String policySetName = "my PS Id";
  String evalId = "e1";
  PolicyData accountPolicy =
      PolicyData.builder().identifier(policyIdentifier).name(policyName).account_id(accountIdentifier).build();
  PolicyData orgPolicy = PolicyData.builder()
                             .identifier(policyIdentifier)
                             .name(policyName)
                             .account_id(accountIdentifier)
                             .org_id(orgIdentifier)
                             .build();
  PolicyData projectPolicy = PolicyData.builder()
                                 .identifier(policyIdentifier)
                                 .name(policyName)
                                 .account_id(accountIdentifier)
                                 .org_id(orgIdentifier)
                                 .project_id(projectIdentifier)
                                 .build();

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToOutcome() {
    OpaPolicyEvaluationResponse successAccPolicyResponse =
        OpaPolicyEvaluationResponse.builder().status("pass").policy(accountPolicy).build();
    OpaPolicySetEvaluationResponse successAccPSResponse =
        OpaPolicySetEvaluationResponse.builder()
            .status("pass")
            .identifier(policySetIdentifier)
            .name(policySetName)
            .account_id(accountIdentifier)
            .details(Collections.singletonList(successAccPolicyResponse))
            .build();

    OpaPolicyEvaluationResponse successProjPolicyResponse =
        OpaPolicyEvaluationResponse.builder().status("pass").policy(projectPolicy).build();
    OpaPolicySetEvaluationResponse successProjPSResponse =
        OpaPolicySetEvaluationResponse.builder()
            .status("pass")
            .identifier(policySetIdentifier)
            .name(policySetName)
            .account_id(accountIdentifier)
            .org_id(orgIdentifier)
            .project_id(projectIdentifier)
            .details(Arrays.asList(successProjPolicyResponse, successAccPolicyResponse))
            .build();

    OpaEvaluationResponseHolder fullResponse = OpaEvaluationResponseHolder.builder()
                                                   .id(evalId)
                                                   .account_id(accountIdentifier)
                                                   .org_id(orgIdentifier)
                                                   .project_id(projectIdentifier)
                                                   .status("pass")
                                                   .details(Arrays.asList(successAccPSResponse, successProjPSResponse))
                                                   .build();
    PolicyStepOutcome policyStepOutcome = PolicyStepOutcomeMapper.toOutcome(fullResponse);
    assertThat(policyStepOutcome.getEvaluationId()).isEqualTo("e1");
    assertThat(policyStepOutcome.getStatus()).isEqualTo("pass");
    assertThat(policyStepOutcome.getPolicySetDetails()).containsOnlyKeys("account.myPSId", "myPSId");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToPolicySetOutcomeAtAccLevel() {
    OpaPolicyEvaluationResponse successPolicyResponse =
        OpaPolicyEvaluationResponse.builder().status("pass").policy(accountPolicy).build();
    OpaPolicySetEvaluationResponse successPSResponse = OpaPolicySetEvaluationResponse.builder()
                                                           .status("pass")
                                                           .identifier(policySetIdentifier)
                                                           .name(policySetName)
                                                           .account_id(accountIdentifier)
                                                           .details(Collections.singletonList(successPolicyResponse))
                                                           .build();
    PolicySetOutcome policySetOutcome = PolicyStepOutcomeMapper.toPolicySetOutcome(successPSResponse);
    assertThat(policySetOutcome.getIdentifier()).isEqualTo("account.myPSId");
    assertThat(policySetOutcome.getName()).isEqualTo("my PS Id");
    assertThat(policySetOutcome.getStatus()).isEqualTo("pass");
    Map<String, PolicyOutcome> policyDetails = policySetOutcome.getPolicyDetails();
    assertThat(policyDetails).containsOnlyKeys("account.myId");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToPolicySetOutcomeAtOrgLevel() {
    OpaPolicyEvaluationResponse successPolicyResponse =
        OpaPolicyEvaluationResponse.builder().status("pass").policy(orgPolicy).build();
    OpaPolicyEvaluationResponse successPolicyResponseAcc =
        OpaPolicyEvaluationResponse.builder().status("pass").policy(accountPolicy).build();
    OpaPolicySetEvaluationResponse successPSResponse =
        OpaPolicySetEvaluationResponse.builder()
            .status("pass")
            .identifier(policySetIdentifier)
            .name(policySetName)
            .account_id(accountIdentifier)
            .org_id(orgIdentifier)
            .details(Arrays.asList(successPolicyResponse, successPolicyResponseAcc))
            .build();
    PolicySetOutcome policySetOutcome = PolicyStepOutcomeMapper.toPolicySetOutcome(successPSResponse);
    assertThat(policySetOutcome.getIdentifier()).isEqualTo("org.myPSId");
    assertThat(policySetOutcome.getName()).isEqualTo("my PS Id");
    assertThat(policySetOutcome.getStatus()).isEqualTo("pass");
    Map<String, PolicyOutcome> policyDetails = policySetOutcome.getPolicyDetails();
    assertThat(policyDetails).containsOnlyKeys("account.myId", "org.myId");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToPolicySetOutcomeAtProjectLevel() {
    OpaPolicyEvaluationResponse successPolicyResponse =
        OpaPolicyEvaluationResponse.builder().status("pass").policy(projectPolicy).build();
    OpaPolicyEvaluationResponse successPolicyResponseOrg =
        OpaPolicyEvaluationResponse.builder().status("pass").policy(orgPolicy).build();
    OpaPolicyEvaluationResponse successPolicyResponseAcc =
        OpaPolicyEvaluationResponse.builder().status("pass").policy(accountPolicy).build();
    OpaPolicySetEvaluationResponse successPSResponse =
        OpaPolicySetEvaluationResponse.builder()
            .status("pass")
            .identifier(policySetIdentifier)
            .name(policySetName)
            .account_id(accountIdentifier)
            .org_id(orgIdentifier)
            .project_id(projectIdentifier)
            .details(Arrays.asList(successPolicyResponse, successPolicyResponseOrg, successPolicyResponseAcc))
            .build();
    PolicySetOutcome policySetOutcome = PolicyStepOutcomeMapper.toPolicySetOutcome(successPSResponse);
    assertThat(policySetOutcome.getIdentifier()).isEqualTo("myPSId");
    assertThat(policySetOutcome.getName()).isEqualTo("my PS Id");
    assertThat(policySetOutcome.getStatus()).isEqualTo("pass");
    Map<String, PolicyOutcome> policyDetails = policySetOutcome.getPolicyDetails();
    assertThat(policyDetails).containsOnlyKeys("account.myId", "org.myId", "myId");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToPolicyOutcomeAtAccLevel() {
    OpaPolicyEvaluationResponse successResponse =
        OpaPolicyEvaluationResponse.builder().status("pass").policy(accountPolicy).build();
    PolicyOutcome policyOutcome = PolicyStepOutcomeMapper.toPolicyOutcome(successResponse);
    assertThat(policyOutcome.getIdentifier()).isEqualTo("account.myId");
    assertThat(policyOutcome.getName()).isEqualTo("my Id");
    assertThat(policyOutcome.getStatus()).isEqualTo("pass");
    assertThat(policyOutcome.getDenyMessages()).isEmpty();
    assertThat(policyOutcome.getError()).isEmpty();

    OpaPolicyEvaluationResponse failureResponse =
        OpaPolicyEvaluationResponse.builder()
            .status("error")
            .policy(accountPolicy)
            .deny_messages(Collections.singletonList("Too many stages. Limit is 4"))
            .build();
    PolicyOutcome policyOutcomeFail = PolicyStepOutcomeMapper.toPolicyOutcome(failureResponse);
    assertThat(policyOutcomeFail.getIdentifier()).isEqualTo("account.myId");
    assertThat(policyOutcomeFail.getName()).isEqualTo("my Id");
    assertThat(policyOutcomeFail.getStatus()).isEqualTo("error");
    assertThat(policyOutcomeFail.getDenyMessages()).containsExactly("Too many stages. Limit is 4");
    assertThat(policyOutcomeFail.getError()).isEmpty();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToPolicyOutcomeAtOrgLevel() {
    OpaPolicyEvaluationResponse successResponse =
        OpaPolicyEvaluationResponse.builder().status("pass").policy(orgPolicy).build();
    PolicyOutcome policyOutcome = PolicyStepOutcomeMapper.toPolicyOutcome(successResponse);
    assertThat(policyOutcome.getIdentifier()).isEqualTo("org.myId");
    assertThat(policyOutcome.getName()).isEqualTo("my Id");
    assertThat(policyOutcome.getStatus()).isEqualTo("pass");
    assertThat(policyOutcome.getDenyMessages()).isEmpty();
    assertThat(policyOutcome.getError()).isEmpty();

    OpaPolicyEvaluationResponse failureResponse =
        OpaPolicyEvaluationResponse.builder()
            .status("error")
            .policy(orgPolicy)
            .deny_messages(Collections.singletonList("Too many stages. Limit is 4"))
            .build();
    PolicyOutcome policyOutcomeFail = PolicyStepOutcomeMapper.toPolicyOutcome(failureResponse);
    assertThat(policyOutcomeFail.getIdentifier()).isEqualTo("org.myId");
    assertThat(policyOutcomeFail.getName()).isEqualTo("my Id");
    assertThat(policyOutcomeFail.getStatus()).isEqualTo("error");
    assertThat(policyOutcomeFail.getDenyMessages()).containsExactly("Too many stages. Limit is 4");
    assertThat(policyOutcomeFail.getError()).isEmpty();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToPolicyOutcomeAtProjectLevel() {
    OpaPolicyEvaluationResponse successResponse =
        OpaPolicyEvaluationResponse.builder().status("pass").policy(projectPolicy).build();
    PolicyOutcome policyOutcome = PolicyStepOutcomeMapper.toPolicyOutcome(successResponse);
    assertThat(policyOutcome.getIdentifier()).isEqualTo("myId");
    assertThat(policyOutcome.getName()).isEqualTo("my Id");
    assertThat(policyOutcome.getStatus()).isEqualTo("pass");
    assertThat(policyOutcome.getDenyMessages()).isEmpty();
    assertThat(policyOutcome.getError()).isEmpty();

    OpaPolicyEvaluationResponse failureResponse =
        OpaPolicyEvaluationResponse.builder()
            .status("error")
            .policy(projectPolicy)
            .deny_messages(Collections.singletonList("Too many stages. Limit is 4"))
            .build();
    PolicyOutcome policyOutcomeFail = PolicyStepOutcomeMapper.toPolicyOutcome(failureResponse);
    assertThat(policyOutcomeFail.getIdentifier()).isEqualTo("myId");
    assertThat(policyOutcomeFail.getName()).isEqualTo("my Id");
    assertThat(policyOutcomeFail.getStatus()).isEqualTo("error");
    assertThat(policyOutcomeFail.getDenyMessages()).containsExactly("Too many stages. Limit is 4");
    assertThat(policyOutcomeFail.getError()).isEmpty();
  }
}
