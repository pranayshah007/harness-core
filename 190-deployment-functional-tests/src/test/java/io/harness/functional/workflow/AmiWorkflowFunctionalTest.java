/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PRAKHAR;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.CDFunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.WorkflowGenerator;
import io.harness.rule.Owner;
import io.harness.testframework.restutils.ArtifactRestUtils;
import io.harness.testframework.restutils.InfrastructureDefinitionRestUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.Application;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureType;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.dl.WingsPersistence;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.aws.model.AwsAsgGetRunningCountData;
import software.wings.service.intfc.InfrastructureMappingService;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class AmiWorkflowFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private InfrastructureMappingService infrastructureMappingService;

  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private WorkflowUtils workflowUtils;
  @Inject private WingsPersistence wingsPersistence;

  private final Seed seed = new Seed(0);
  private Owners owners;

  private Service service;
  private Application application;
  private Environment environment;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    application = applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.GENERIC_TEST);
    environment = environmentGenerator.ensurePredefined(seed, owners, EnvironmentGenerator.Environments.GENERIC_TEST);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(CDFunctionalTests.class)
  public void shouldRunAwsAmiWorkflow() {
    service = serviceGenerator.ensureAmiGenericTest(seed, owners, "aws-ami");
    final String accountId = service.getAccountId();
    final String appId = service.getAppId();

    resetCache(service.getAccountId());

    InfrastructureDefinition amiInfrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureType.AWS_AMI, bearerToken);
    ensureInfraMapping(service, amiInfrastructureDefinition);
    final String envId = amiInfrastructureDefinition.getEnvId();

    Workflow bgWorkflow = workflowUtils.createAwsAmiBGWorkflow("ami-bg-", service, amiInfrastructureDefinition);

    bgWorkflow =
        WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), application.getUuid(), bgWorkflow);

    resetCache(service.getAccountId());

    Artifact artifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, appId, service.getArtifactStreamIds().get(0), 0);

    AwsAsgGetRunningCountData runningCountData = InfrastructureDefinitionRestUtils.amiRunningInstances(
        bearerToken, accountId, appId, service.getUuid(), amiInfrastructureDefinition.getUuid());

    assertThat(runningCountData).isNotNull();

    final WorkflowExecution workflowExecution =
        runWorkflow(bearerToken, appId, envId, bgWorkflow.getUuid(), ImmutableList.of(artifact));

    List<InfrastructureMapping> infrastructureMappings =
        infrastructureMappingService.getInfraMappingLinkedToInfraDefinition(
            appId, amiInfrastructureDefinition.getUuid());

    assertThat(infrastructureMappings).hasSize(1);
    AwsAmiInfrastructureMapping amiInfraMapping = (AwsAmiInfrastructureMapping) infrastructureMappings.get(0);
    AwsAmiInfrastructure amiInfrastructure = (AwsAmiInfrastructure) amiInfrastructureDefinition.getInfrastructure();
    assertThat(amiInfraMapping.getRegion()).isEqualTo(amiInfrastructure.getRegion());
    assertThat(amiInfraMapping.getAutoScalingGroupName()).isEqualTo(amiInfrastructure.getAutoScalingGroupName());
    assertThat(amiInfraMapping.getClassicLoadBalancers()).isEqualTo(amiInfrastructure.getClassicLoadBalancers());
    assertThat(amiInfraMapping.getTargetGroupArns()).isEqualTo(amiInfrastructure.getTargetGroupArns());
    assertThat(amiInfraMapping.getHostNameConvention()).isEqualTo(amiInfrastructure.getHostNameConvention());
    assertThat(amiInfraMapping.getStageClassicLoadBalancers())
        .isEqualTo(amiInfrastructure.getStageClassicLoadBalancers());
    assertThat(amiInfraMapping.getStageTargetGroupArns()).isEqualTo(amiInfrastructure.getStageTargetGroupArns());
    assertThat(amiInfraMapping.getAmiDeploymentType()).isEqualTo(amiInfrastructure.getAmiDeploymentType());
    assertThat(amiInfraMapping.getSpotinstCloudProvider()).isEqualTo(amiInfrastructure.getSpotinstCloudProvider());
    assertThat(amiInfraMapping.getSpotinstElastiGroupJson()).isEqualTo(amiInfrastructure.getSpotinstElastiGroupJson());

    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertInstanceCount(workflowExecution.getStatus(), appId, infrastructureMappings.get(0).getUuid(),
        amiInfrastructureDefinition.getUuid());
    // TODO: delete ASG
  }

  @Test
  @Owner(developers = PRAKHAR)
  @Category(CDFunctionalTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldRunAwsAmiRollbackWorkflow() {
    service = serviceGenerator.ensureAmiGenericTest(seed, owners, "aws-ami");
    final String accountId = service.getAccountId();
    final String appId = service.getAppId();

    resetCache(service.getAccountId());

    InfrastructureDefinition amiInfrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureType.AWS_AMI, bearerToken);
    ensureInfraMapping(service, amiInfrastructureDefinition);
    final String envId = amiInfrastructureDefinition.getEnvId();

    Workflow bgWorkflow = workflowUtils.createAwsAmiBGRollbackWorkflow("ami-bg-", service, amiInfrastructureDefinition);

    bgWorkflow =
        WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), application.getUuid(), bgWorkflow);

    resetCache(service.getAccountId());

    Artifact artifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, appId, service.getArtifactStreamIds().get(0), 0);

    AwsAsgGetRunningCountData runningCountData = InfrastructureDefinitionRestUtils.amiRunningInstances(
        bearerToken, accountId, appId, service.getUuid(), amiInfrastructureDefinition.getUuid());

    assertThat(runningCountData).isNotNull();

    final WorkflowExecution workflowExecution =
        runWorkflow(bearerToken, appId, envId, bgWorkflow.getUuid(), ImmutableList.of(artifact));

    List<InfrastructureMapping> infrastructureMappings =
        infrastructureMappingService.getInfraMappingLinkedToInfraDefinition(
            appId, amiInfrastructureDefinition.getUuid());

    assertThat(infrastructureMappings).hasSize(1);
    AwsAmiInfrastructureMapping amiInfraMapping = (AwsAmiInfrastructureMapping) infrastructureMappings.get(0);
    AwsAmiInfrastructure amiInfrastructure = (AwsAmiInfrastructure) amiInfrastructureDefinition.getInfrastructure();
    assertThat(amiInfraMapping.getRegion()).isEqualTo(amiInfrastructure.getRegion());
    assertThat(amiInfraMapping.getAutoScalingGroupName()).isEqualTo(amiInfrastructure.getAutoScalingGroupName());
    assertThat(amiInfraMapping.getClassicLoadBalancers()).isEqualTo(amiInfrastructure.getClassicLoadBalancers());
    assertThat(amiInfraMapping.getTargetGroupArns()).isEqualTo(amiInfrastructure.getTargetGroupArns());
    assertThat(amiInfraMapping.getHostNameConvention()).isEqualTo(amiInfrastructure.getHostNameConvention());
    assertThat(amiInfraMapping.getStageClassicLoadBalancers())
        .isEqualTo(amiInfrastructure.getStageClassicLoadBalancers());
    assertThat(amiInfraMapping.getStageTargetGroupArns()).isEqualTo(amiInfrastructure.getStageTargetGroupArns());
    assertThat(amiInfraMapping.getAmiDeploymentType()).isEqualTo(amiInfrastructure.getAmiDeploymentType());
    assertThat(amiInfraMapping.getSpotinstCloudProvider()).isEqualTo(amiInfrastructure.getSpotinstCloudProvider());
    assertThat(amiInfraMapping.getSpotinstElastiGroupJson()).isEqualTo(amiInfrastructure.getSpotinstElastiGroupJson());

    workflowUtils.assertRollbackInWorkflowExecution(workflowExecution);
    assertInstanceCount(workflowExecution.getStatus(), appId, infrastructureMappings.get(0).getUuid(),
        amiInfrastructureDefinition.getUuid());
    // TODO: delete ASG
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(CDFunctionalTests.class)
  public void shouldRunAwsAmiWorkflow_Launchtemplate() {
    service = serviceGenerator.ensureAmiGenericTest(seed, owners, "aws-ami-lt");
    final String accountId = service.getAccountId();
    final String appId = service.getAppId();

    resetCache(accountId);

    InfrastructureDefinition amiInfrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureType.AWS_AMI_LT, bearerToken);
    ensureInfraMapping(service, amiInfrastructureDefinition);

    final String envId = amiInfrastructureDefinition.getEnvId();

    Workflow bgWorkflow = workflowUtils.createAwsAmiBGWorkflow("ami-bg-lt-", service, amiInfrastructureDefinition);

    bgWorkflow =
        WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), application.getUuid(), bgWorkflow);
    resetCache(service.getAccountId());

    Artifact artifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, appId, service.getArtifactStreamIds().get(0), 0);

    final WorkflowExecution workflowExecution =
        runWorkflow(bearerToken, appId, envId, bgWorkflow.getUuid(), ImmutableList.of(artifact));
    List<InfrastructureMapping> infrastructureMappings =
        infrastructureMappingService.getInfraMappingLinkedToInfraDefinition(
            appId, amiInfrastructureDefinition.getUuid());
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertInstanceCount(workflowExecution.getStatus(), appId, infrastructureMappings.get(0).getUuid(),
        amiInfrastructureDefinition.getUuid());
    //  todo delete ASG
  }

  private String ensureInfraMapping(Service service, InfrastructureDefinition amiInfrastructureDefinition) {
    // ensure inframapping is always created with the same id, as the id is used for tagging resources
    final InfrastructureMapping infraMapping = amiInfrastructureDefinition.getInfraMapping();
    infraMapping.setName(amiInfrastructureDefinition.getName() + "_inframapping");
    infraMapping.setUuid(amiInfrastructureDefinition.getName() + "_inframapping_id");
    infraMapping.setServiceId(service.getUuid());
    infraMapping.setInfrastructureDefinitionId(amiInfrastructureDefinition.getUuid());
    infraMapping.setAccountId(service.getAccountId());
    return wingsPersistence.save(infraMapping);
  }
}
