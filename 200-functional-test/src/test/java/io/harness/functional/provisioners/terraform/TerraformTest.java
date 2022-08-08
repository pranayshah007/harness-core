/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.provisioners.terraform;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.POST_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.utils.CryptoUtils.secureRandAlphaNumString;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.WorkflowType;
import io.harness.category.element.FunctionalTests;
import io.harness.ff.FeatureFlagService;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.functional.provisioners.utils.InfraProvisionerUtils;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.ApplicationGenerator.Applications;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.EnvironmentGenerator.Environments;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.SecretGenerator;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.ServiceGenerator.Services;
import io.harness.generator.SettingGenerator;
import io.harness.generator.SettingGenerator.Settings;
import io.harness.generator.WorkflowGenerator;
import io.harness.rule.Owner;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;
import io.harness.testframework.restutils.InfraProvisionerRestUtils;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.GraphNode;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.NameValuePair;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.sm.StateType;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class TerraformTest extends AbstractFunctionalTest {
  private static final String secretKeyName = "aws_playground_secret_key";
  private static final long TEST_TIMEOUT_IN_MINUTES = 3;

  private final Seed seed = new Seed(0);
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private SettingGenerator settingGenerator;
  @Inject private SecretGenerator secretGenerator;
  @Inject private ScmSecret scmSecret;
  @Inject private WorkflowUtils workflowUtils;
  @Inject private WorkflowGenerator workflowGenerator;
  @Inject FeatureFlagService featureFlagService;

  private Application application;
  private Service service;
  private Environment environment;
  private TerraformInfrastructureProvisioner terraformInfrastructureProvisioner;
  private Workflow workflow;
  private SettingAttribute settingAttribute;
  private String secretKeyValue;

  private Owners owners;

  @Before
  public void setUp() throws Exception {
    owners = ownerManager.create();
  }

  private void setupStuff(Applications applicationType, Services serviceType, Environments environmentType,
      Settings settings) throws Exception {
    ensurePredefinedStuff(applicationType, serviceType, environmentType, settings);

    if (!featureFlagService.isEnabled(FeatureName.GIT_ACCOUNT_SUPPORT, application.getAccountId())) {
      featureFlagService.enableAccount(FeatureName.GIT_ACCOUNT_SUPPORT, application.getAccountId());
    }

    resetCache(application.getAccountId());
    terraformInfrastructureProvisioner = buildProvisionerObject();

    terraformInfrastructureProvisioner = (TerraformInfrastructureProvisioner) InfraProvisionerRestUtils.saveProvisioner(
        application.getAppId(), bearerToken, terraformInfrastructureProvisioner);

    workflow = buildWorkflow(terraformInfrastructureProvisioner);
    workflow = workflowGenerator.ensureWorkflow(seed, owners, workflow);
  }

  @Test
  @Owner(developers = YOGESH, intermittent = true)
  @Category(FunctionalTests.class)
  public void shouldRunTerraformWorkflow() throws Exception {
    setupStuff(Applications.FUNCTIONAL_TEST, Services.FUNCTIONAL_TEST, Environments.FUNCTIONAL_TEST,
        Settings.TERRAFORM_MAIN_GIT_REPO);

    ExecutionArgs executionArgs = prepareExecutionArgs(workflow);
    WorkflowExecution workflowExecution =
        runWorkflow(bearerToken, application.getUuid(), environment.getUuid(), executionArgs);
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = YOGESH, intermittent = true)
  @Category(FunctionalTests.class)
  public void shouldFetchTerraformTargets() throws Exception {
    setupStuff(Applications.FUNCTIONAL_TEST, Services.FUNCTIONAL_TEST, Environments.FUNCTIONAL_TEST,
        Settings.TERRAFORM_MAIN_GIT_REPO);

    final String accountId = terraformInfrastructureProvisioner.getAccountId();
    final String appId = terraformInfrastructureProvisioner.getAppId();
    final String provisonerId = terraformInfrastructureProvisioner.getUuid();
    final List<String> terraformVariables =
        InfraProvisionerRestUtils.getTerraformTargets(accountId, appId, bearerToken, provisonerId);
    assertThat(terraformVariables).containsExactlyInAnyOrder("local_file.foo");
  }

  @Test
  @Owner(developers = YOGESH, intermittent = true)
  @Category(FunctionalTests.class)
  public void shouldFetchTerraformVariables() throws Exception {
    setupStuff(Applications.FUNCTIONAL_TEST, Services.FUNCTIONAL_TEST, Environments.FUNCTIONAL_TEST,
        Settings.TERRAFORM_MAIN_GIT_REPO);

    final String accountId = terraformInfrastructureProvisioner.getAccountId();
    final String appId = terraformInfrastructureProvisioner.getAppId();
    final String scmSettingId = terraformInfrastructureProvisioner.getSourceRepoSettingId();
    final String branch = terraformInfrastructureProvisioner.getSourceRepoBranch();
    final String path = terraformInfrastructureProvisioner.getPath();

    resetCache(accountId);

    final List<NameValuePair> terraformVariables = InfraProvisionerRestUtils.getTerraformVariables(
        accountId, appId, bearerToken, scmSettingId, branch, path, null);
    assertThat(terraformVariables).isNotEmpty();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void shouldFetchTerraformVariablesWithAccountGitConnector() throws Exception {
    setupStuff(
        Applications.GENERIC_TEST, Services.GENERIC_TEST, Environments.GENERIC_TEST, Settings.TERRAFORM_MAIN_GIT_AC);

    final String accountId = terraformInfrastructureProvisioner.getAccountId();
    final String appId = terraformInfrastructureProvisioner.getAppId();
    final String scmSettingId = terraformInfrastructureProvisioner.getSourceRepoSettingId();
    final String branch = terraformInfrastructureProvisioner.getSourceRepoBranch();
    final String path = terraformInfrastructureProvisioner.getPath();

    resetCache(accountId);

    final List<NameValuePair> terraformVariables = InfraProvisionerRestUtils.getTerraformVariables(
        accountId, appId, bearerToken, scmSettingId, branch, path, "terraform");
    assertThat(terraformVariables).isNotEmpty();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void shouldFetchTerraformTargetsWithAccountGitConnector() throws Exception {
    setupStuff(
        Applications.GENERIC_TEST, Services.GENERIC_TEST, Environments.GENERIC_TEST, Settings.TERRAFORM_MAIN_GIT_AC);

    final String accountId = terraformInfrastructureProvisioner.getAccountId();
    final String appId = terraformInfrastructureProvisioner.getAppId();
    final String provisonerId = terraformInfrastructureProvisioner.getUuid();
    final List<String> terraformVariables =
        InfraProvisionerRestUtils.getTerraformTargets(accountId, appId, bearerToken, provisonerId);
    assertThat(terraformVariables).containsExactlyInAnyOrder("local_file.foo");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void shouldRunTerraformWorkflowWithAccountGitConnector() throws Exception {
    setupStuff(
        Applications.GENERIC_TEST, Services.GENERIC_TEST, Environments.GENERIC_TEST, Settings.TERRAFORM_MAIN_GIT_AC);

    ExecutionArgs executionArgs = prepareExecutionArgs(workflow);
    WorkflowExecution workflowExecution =
        runWorkflow(bearerToken, application.getUuid(), environment.getUuid(), executionArgs);
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  private void ensurePredefinedStuff(
      Applications applicationType, Services serviceType, Environments environmentType, Settings settings) {
    application = applicationGenerator.ensurePredefined(seed, owners, applicationType);
    service = serviceGenerator.ensurePredefined(seed, owners, serviceType);
    environment = environmentGenerator.ensurePredefined(seed, owners, environmentType);
    settingAttribute = settingGenerator.ensurePredefined(seed, owners, settings);
    secretKeyValue = secretGenerator.ensureStored(owners, SecretName.builder().value(secretKeyName).build());
  }

  private ExecutionArgs prepareExecutionArgs(Workflow workflow) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(workflow.getWorkflowType());
    executionArgs.setOrchestrationId(workflow.getUuid());
    return executionArgs;
  }

  private Workflow buildWorkflow(TerraformInfrastructureProvisioner terraformInfrastructureProvisioner)
      throws Exception {
    return aWorkflow()
        .name("Terraform Test")
        .envId(environment.getUuid())
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(
            aCanaryOrchestrationWorkflow()
                .withPreDeploymentSteps(
                    aPhaseStep(PRE_DEPLOYMENT)
                        .addStep(buildTerraformProvisionStep(terraformInfrastructureProvisioner.getUuid()))
                        .build())
                .withPostDeploymentSteps(
                    aPhaseStep(POST_DEPLOYMENT)
                        .addStep(buildTerraformDestroyStep(terraformInfrastructureProvisioner.getUuid()))
                        .build())
                .build())
        .build();
  }

  private GraphNode buildTerraformDestroyStep(String provisionerId) {
    return GraphNode.builder()
        .id(generateUuid())
        .type(StateType.TERRAFORM_DESTROY.getType())
        .name("Terraform Destroy")
        .properties(ImmutableMap.<String, Object>builder().put("provisionerId", provisionerId).build())
        .build();
  }
  private GraphNode buildTerraformProvisionStep(String provisionerId) throws Exception {
    InfrastructureProvisioner provisioner = terraformInfrastructureProvisioner;
    provisioner = InfraProvisionerUtils.setValuesToProvisioner(provisioner, scmSecret, secretKeyValue);
    List<Map<String, String>> variables = new ArrayList<>();
    provisioner.getVariables().forEach(var -> variables.add(new HashMap<String, String>() {
      {
        put("name", var.getName());
        put("value", var.getValue());
        put("valueType", var.getValueType());
      }
    }));
    return GraphNode.builder()
        .id(generateUuid())
        .type(StateType.TERRAFORM_PROVISION.getType())
        .name("Terraform Provision")
        .properties(ImmutableMap.<String, Object>builder()
                        .put("provisionerId", provisionerId)
                        .put("variables", variables)
                        .build())
        .build();
  }

  private TerraformInfrastructureProvisioner buildProvisionerObject() {
    List<NameValuePair> variables = Arrays.asList(
        NameValuePair.builder().name("access_key").valueType(ServiceVariableType.TEXT.toString()).build(),
        NameValuePair.builder().name("secret_key").valueType(ServiceVariableType.ENCRYPTED_TEXT.toString()).build());
    return TerraformInfrastructureProvisioner.builder()
        .appId(application.getAppId())
        .name("Terraform Test" + secureRandAlphaNumString(5))
        .repoName("terraform")
        .sourceRepoSettingId(settingAttribute.getUuid())
        .sourceRepoBranch("master")
        .path("functionalTest/")
        .variables(variables)
        .build();
  }
}
