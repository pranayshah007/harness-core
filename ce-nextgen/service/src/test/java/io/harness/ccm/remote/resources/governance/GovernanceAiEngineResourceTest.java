/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.governance;

import static io.harness.rule.OwnerRule.ANMOL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.views.dto.GovernanceAiEngineRequestDTO;
import io.harness.ccm.views.dto.GovernanceAiEngineResponseDTO;
import io.harness.ccm.views.dto.GovernancePromptRule;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.ccm.views.service.GovernanceAiEngineService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GovernanceAiEngineResourceTest extends CategoryTest {
  private GovernanceAiEngineService governanceAiEngineService = mock(GovernanceAiEngineService.class);
  private CENextGenConfiguration ceNextGenConfiguration = mock(CENextGenConfiguration.class);
  private GovernanceAiEngineResource governanceAiEngineResource;
  private final String ACCOUNT_ID = "accountId";
  private final String EC2_RESOURCE_TYPE = "ec2";
  private final Set<String> AWS_RESOURCES = new HashSet<>() {
    {
      add("ami");
      add("asg");
      add("app-elb");
      add("cache-cluster");
      add("ebs");
    }
  };
  private final Set<String> AZURE_RESOURCES = new HashSet<>() {
    {
      add("azure.cosmosdb");
      add("azure.disk");
      add("azure.keyvault");
      add("azure.loadbalancer");
      add("azure.networkinterface");
    }
  };

  @Before
  public void setUp() {
    governanceAiEngineResource = new GovernanceAiEngineResource(ceNextGenConfiguration, governanceAiEngineService);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void aiengine_GeneratePolicy() {
    RuleCloudProviderType ruleCloudProviderType = RuleCloudProviderType.AWS;
    GovernanceAiEngineRequestDTO governanceAiEngineRequestDTO =
        GovernanceAiEngineRequestDTO.builder()
            .resourceType(EC2_RESOURCE_TYPE)
            .isExplain(false)
            .ruleCloudProviderType(ruleCloudProviderType)
            .prompt("Stop all EC2s where instance age is more than 100 days")
            .build();
    GovernanceAiEngineResponseDTO governanceAiEngineResponseDTO =
        GovernanceAiEngineResponseDTO.builder()
            .text(
                "policies:\n- name: ec2-old-instances-stop-age100Days\n  resource: ec2\n  description: |\n    Stop all EC2s where instance age is more than 100 days\n  filters:\n  - State.Name: running\n  - type: instance-age\n    days: 100\n  actions:\n  - type: stop\n")
            .isValid(true)
            .error("")
            .build();
    when(governanceAiEngineService.getAiEngineResponse(
             ACCOUNT_ID, ceNextGenConfiguration.getAiEngineConfig(), governanceAiEngineRequestDTO))
        .thenReturn(governanceAiEngineResponseDTO);
    final ResponseDTO<GovernanceAiEngineResponseDTO> result =
        governanceAiEngineResource.aiengine(ACCOUNT_ID, governanceAiEngineRequestDTO);
    assertThat(result.getData()).isEqualTo(governanceAiEngineResponseDTO);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void aiengine_ExplainPolicy() {
    GovernanceAiEngineRequestDTO governanceAiEngineRequestDTO =
        GovernanceAiEngineRequestDTO.builder()
            .isExplain(true)
            .prompt(
                "policies:\n  - name: asg-unused-list\n    resource: asg\n    description: List any unused ASG\n    filters:\n      - type: value\n        key: MinSize\n        value: 0\n        op: eq\n      - type: value\n        key: DesiredCapacity\n        value: 0\n        op: eq")
            .build();
    GovernanceAiEngineResponseDTO governanceAiEngineResponseDTO =
        GovernanceAiEngineResponseDTO.builder()
            .text(
                "This Cloud Custodian YAML policy is designed to list any unused Auto Scaling Groups (ASGs). \n\nThe policy is defined under the \"policies\" section and has a name of \"asg-unused-list\". The resource being targeted is an ASG, which is specified under the \"resource\" field. \n\nThe policy description is \"List any unused ASG\", which gives an idea of what the policy is intended to do. \n\nThe \"filters\" section is where the conditions for the policy are defined. In this case, there are two filters being applied to the ASG resource. \n\nThe first filter is a \"value\" filter, which checks the \"MinSize\" key of the ASG resource. The value of \"0\" is specified, and the \"op\" (operator) is set to \"eq\" (equals). This filter ensures that the ASG has a minimum size of 0, meaning that it is not currently in use. \n\nThe second filter is also a \"value\" filter, which checks the \"DesiredCapacity\" key of the ASG resource. The value of \"0\" is specified, and the \"op\" is set to \"eq\". This filter ensures that the desired capacity of the ASG is also 0, indicating that it is not currently in use. \n\nOverall, this policy will list any ASGs that have a minimum size and desired capacity of 0, indicating that they are not currently being used.")
            .build();
    when(governanceAiEngineService.getAiEngineResponse(
             ACCOUNT_ID, ceNextGenConfiguration.getAiEngineConfig(), governanceAiEngineRequestDTO))
        .thenReturn(governanceAiEngineResponseDTO);
    final ResponseDTO<GovernanceAiEngineResponseDTO> result =
        governanceAiEngineResource.aiengine(ACCOUNT_ID, governanceAiEngineRequestDTO);
    assertThat(result.getData()).isEqualTo(governanceAiEngineResponseDTO);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void getPromptResources_AWSResources() {
    RuleCloudProviderType ruleCloudProviderType = RuleCloudProviderType.AWS;
    when(governanceAiEngineService.getGovernancePromptResources(ruleCloudProviderType)).thenReturn(AWS_RESOURCES);
    final ResponseDTO<Set<String>> result =
        governanceAiEngineResource.getPromptResources(ACCOUNT_ID, ruleCloudProviderType);
    assertThat(result.getData()).isEqualTo(AWS_RESOURCES);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void getPromptResources_AzureResources() {
    RuleCloudProviderType ruleCloudProviderType = RuleCloudProviderType.AZURE;
    when(governanceAiEngineService.getGovernancePromptResources(ruleCloudProviderType)).thenReturn(AZURE_RESOURCES);
    final ResponseDTO<Set<String>> result =
        governanceAiEngineResource.getPromptResources(ACCOUNT_ID, ruleCloudProviderType);
    assertThat(result.getData()).isEqualTo(AZURE_RESOURCES);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void getPromptRules_AWSEc2Rules() {
    RuleCloudProviderType ruleCloudProviderType = RuleCloudProviderType.AWS;
    List<GovernancePromptRule> ec2PromptRules = List.of(
        GovernancePromptRule.builder()
            .description("Stop all EC2s where instance age is more than 7 days")
            .ruleYaml(
                "policies:\n- name: ec2-old-instances-stop-age7Days\n  resource: ec2\n  description: |\n    Stop all EC2s where instance age is more than 7 days\n  filters:\n  - State.Name: running\n  - type: instance-age\n    days: 7\n  actions:\n  - type: stop\n")
            .build());
    when(governanceAiEngineService.getGovernancePromptRules(ruleCloudProviderType, EC2_RESOURCE_TYPE))
        .thenReturn(ec2PromptRules);
    final ResponseDTO<List<GovernancePromptRule>> result =
        governanceAiEngineResource.getPromptRules(ACCOUNT_ID, ruleCloudProviderType, EC2_RESOURCE_TYPE);
    assertThat(result.getData()).isEqualTo(ec2PromptRules);
  }
}
