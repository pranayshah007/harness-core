package io.harness.engine;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.opaclient.model.OpaPolicyEvaluationResponse;
import io.harness.opaclient.model.OpaPolicySetEvaluationResponse;
import io.harness.opaclient.model.PipelineOpaEvaluationContext;
import io.harness.opaclient.model.PolicyData;
import io.harness.pms.contracts.governance.GovernanceMetadata;
import io.harness.pms.contracts.governance.PolicyMetadata;
import io.harness.pms.contracts.governance.PolicySetMetadata;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.UserPrincipal;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class GovernanceServiceHelperTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetEntityString() throws UnsupportedEncodingException {
    String entityString = GovernanceServiceHelper.getEntityString("acc", "org", "proj", "pipeline");
    assertThat(entityString)
        .isEqualTo(
            "accountIdentifier%3Aacc%2ForgIdentifier%3Aorg%2FprojectIdentifier%3Aproj%2FpipelineIdentifier%3Apipeline");

    entityString = GovernanceServiceHelper.getEntityString("acc", "org", "proj", "pipe_line");
    assertThat(entityString)
        .isEqualTo(
            "accountIdentifier%3Aacc%2ForgIdentifier%3Aorg%2FprojectIdentifier%3Aproj%2FpipelineIdentifier%3Apipe_line");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetEntityMetadataString() throws UnsupportedEncodingException {
    String entityString = GovernanceServiceHelper.getEntityMetadataString("p1", "p1", "exec");
    assertThat(entityString)
        .isEqualTo(
            "%7B%22pipelineIdentifier%22%3A%22p1%22%2C%22entityName%22%3A%22p1%22%2C%22executionIdentifier%22%3A%22exec%22%7D");
    entityString = GovernanceServiceHelper.getEntityMetadataString("p_one", "p one", "exec");
    assertThat(entityString)
        .isEqualTo(
            "%7B%22pipelineIdentifier%22%3A%22p_one%22%2C%22entityName%22%3A%22p+one%22%2C%22executionIdentifier%22%3A%22exec%22%7D");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMapResponseToMetadata() {
    PolicyData policyData =
        PolicyData.builder().account_id("acc").identifier("myPol1").name("my Pol1").created(15L).build();
    OpaPolicyEvaluationResponse policyResponse = OpaPolicyEvaluationResponse.builder()
                                                     .status("error")
                                                     .deny_messages(Collections.singletonList("this is wrong"))
                                                     .policy(policyData)
                                                     .build();
    OpaPolicySetEvaluationResponse policySetResponse = OpaPolicySetEvaluationResponse.builder()
                                                           .status("error")
                                                           .identifier("psID")
                                                           .name("myName")
                                                           .details(Collections.singletonList(policyResponse))
                                                           .created(20L)
                                                           .account_id("acc")
                                                           .build();
    OpaEvaluationResponseHolder evaluationResponse = OpaEvaluationResponseHolder.builder()
                                                         .id("id01")
                                                         .status("error")
                                                         .details(Collections.singletonList(policySetResponse))
                                                         .account_id("acc")
                                                         .org_id("org")
                                                         .project_id("proj")
                                                         .entity("thisEntity")
                                                         .type("type")
                                                         .action("onSave")
                                                         .created(99L)
                                                         .build();
    GovernanceMetadata governanceMetadata = GovernanceServiceHelper.mapResponseToMetadata(evaluationResponse);
    assertThat(governanceMetadata).isNotNull();
    assertThat(governanceMetadata.getId()).isEqualTo("id01");
    assertThat(governanceMetadata.getDeny()).isTrue();
    assertThat(governanceMetadata.getStatus()).isEqualTo("error");
    assertThat(governanceMetadata.getAccountId()).isEqualTo("acc");
    assertThat(governanceMetadata.getOrgId()).isEqualTo("org");
    assertThat(governanceMetadata.getProjectId()).isEqualTo("proj");
    assertThat(governanceMetadata.getEntity()).isEqualTo("thisEntity");
    assertThat(governanceMetadata.getType()).isEqualTo("type");
    assertThat(governanceMetadata.getAction()).isEqualTo("onSave");
    assertThat(governanceMetadata.getCreated()).isEqualTo(99L);

    List<PolicySetMetadata> policySetDetails = governanceMetadata.getDetailsList();
    assertThat(policySetDetails).hasSize(1);
    PolicySetMetadata policySetMetadata = policySetDetails.get(0);
    assertThat(policySetMetadata.getDeny()).isTrue();
    assertThat(policySetMetadata.getStatus()).isEqualTo("error");
    assertThat(policySetMetadata.getPolicySetName()).isEqualTo("myName");
    assertThat(policySetMetadata.getIdentifier()).isEqualTo("psID");
    assertThat(policySetMetadata.getCreated()).isEqualTo(20L);
    assertThat(policySetMetadata.getAccountId()).isEqualTo("acc");
    assertThat(policySetMetadata.getOrgId()).isEqualTo("");
    assertThat(policySetMetadata.getProjectId()).isEqualTo("");

    List<PolicyMetadata> policyMetadataList = policySetMetadata.getPolicyMetadataList();
    assertThat(policyMetadataList).hasSize(1);
    PolicyMetadata policyMetadata = policyMetadataList.get(0);
    assertThat(policyMetadata.getPolicyName()).isEqualTo("my Pol1");
    assertThat(policyMetadata.getIdentifier()).isEqualTo("myPol1");
    assertThat(policyMetadata.getAccountId()).isEqualTo("acc");
    assertThat(policyMetadata.getOrgId()).isEqualTo("");
    assertThat(policyMetadata.getProjectId()).isEqualTo("");
    assertThat(policyMetadata.getCreated()).isEqualTo(15L);
    assertThat(policyMetadata.getStatus()).isEqualTo("error");
    assertThat(policyMetadata.getSeverity()).isEqualTo("error");
    assertThat(policyMetadata.getDenyMessagesList()).containsExactly("this is wrong");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testEmptyPolicySetResponse() {
    assertThat(GovernanceServiceHelper.mapPolicySetMetadata(null)).isEmpty();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testEmptyPolicyResponse() {
    assertThat(GovernanceServiceHelper.mapPolicyMetadata(null)).isEmpty();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreateEvaluationContext() throws IOException {
    long timeBeforeMethodCall = System.currentTimeMillis();
    String yaml = "simple: yaml";
    PipelineOpaEvaluationContext evaluationContext = GovernanceServiceHelper.createEvaluationContext(yaml);
    assertThat(evaluationContext.getDate().getTime()).isGreaterThan(timeBeforeMethodCall);
    assertThat(evaluationContext.getDate().getTime()).isLessThan(System.currentTimeMillis());
    assertThat(evaluationContext.getPipeline()).isNotNull();
    assertThat(((Map<?, ?>) evaluationContext.getPipeline()).get("simple")).isEqualTo("yaml");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetUserIdentifier() {
    assertThat(GovernanceServiceHelper.getUserIdentifier()).isEqualTo("");

    Principal serviceAccountPrincipal = new ServiceAccountPrincipal("", "", "");
    SourcePrincipalContextBuilder.setSourcePrincipal(serviceAccountPrincipal);
    assertThat(GovernanceServiceHelper.getUserIdentifier()).isEqualTo("");

    Principal userPrincipal = new UserPrincipal("some name", "email@email.com", "someName", "acc");
    SourcePrincipalContextBuilder.setSourcePrincipal(userPrincipal);
    assertThat(GovernanceServiceHelper.getUserIdentifier()).isEqualTo("some name");
  }
}