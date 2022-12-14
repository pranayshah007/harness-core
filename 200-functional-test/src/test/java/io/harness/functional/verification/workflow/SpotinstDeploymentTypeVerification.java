/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.verification.workflow;

import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.FunctionalTests;
import io.harness.context.ContextElementType;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.SettingGenerator;
import io.harness.generator.WorkflowGenerator;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.testframework.restutils.ArtifactRestUtils;

import software.wings.beans.AwsConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SpotInstConfig;
import software.wings.beans.Workflow;
import software.wings.dl.WingsPersistence;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.spotinst.SpotinstHelperServiceManager;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ContextElement;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;
import software.wings.sm.states.spotinst.SpotInstSetupContextElement;

import com.amazonaws.services.ec2.model.Instance;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SpotinstDeploymentTypeVerification extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private WorkflowUtils workflowUtils;
  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private SettingGenerator settingGenerator;
  @Inject private SpotinstHelperServiceManager spotinstHelperServiceManager;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private SettingsService settingsService;
  @Inject private SecretManager secretManager;

  private final Seed seed = new Seed(0);
  private Owners owners;
  private Service service;

  @Before
  public void setUp() {
    owners = ownerManager.create();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(FunctionalTests.class)
  @Ignore("Ignored due to absence of long term aws keys")
  public void testCanaryVerification() throws IOException {
    service = serviceGenerator.ensureSpotinstAmiGenericTest(seed, owners, "aws-spotinst");
    final SettingAttribute elkConnector =
        settingGenerator.ensurePredefined(seed, owners, SettingGenerator.Settings.ELK);
    final String accountId = service.getAccountId();
    final String appId = service.getAppId();

    resetCache(accountId);

    InfrastructureDefinition spotinstInfraDef =
        infrastructureDefinitionGenerator.ensureSpotinstAmiDeployment(seed, owners, bearerToken);

    final String envId = spotinstInfraDef.getEnvId();

    Workflow canaryWorkflow = workflowUtils.createSpotinstCanaryWorkflowWithVerifyStep(
        "spotinst-canary-", service, spotinstInfraDef, elkConnector.getUuid());

    canaryWorkflow = workflowGenerator.ensureWorkflow(seed, owners, canaryWorkflow);

    resetCache(accountId);

    Artifact artifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, appId, service.getArtifactStreamIds().get(0), 0);

    runWorkflow(bearerToken, appId, envId, canaryWorkflow.getUuid(), ImmutableList.of(artifact));

    AnalysisContext analysisContext =
        runWorkflowWithVerification(bearerToken, appId, envId, canaryWorkflow.getUuid(), ImmutableList.of(artifact));

    assertThat(analysisContext).isNotNull();

    StateExecutionInstance instance =
        wingsPersistence.createQuery(StateExecutionInstance.class, excludeAuthority)
            .filter(StateExecutionInstanceKeys.executionUuid, analysisContext.getWorkflowExecutionId())
            .filter(StateExecutionInstanceKeys.stateName, "Verify Canary")
            .get();

    assertThat(instance).isNotNull();
    Optional<ContextElement> spotinstContextElement =
        instance.getContextElements()
            .stream()
            .filter(contextElement -> contextElement.getElementType() == ContextElementType.SPOTINST_SERVICE_SETUP)
            .findAny();

    assertThat(spotinstContextElement).isPresent();

    if (spotinstContextElement.isPresent()) {
      SpotInstSetupContextElement spotInstSetupContextElement =
          (SpotInstSetupContextElement) spotinstContextElement.get();
      String oldElastigroupId = spotInstSetupContextElement.getOldElastiGroupOriginalConfig().getId();
      String newElastigroupId = spotInstSetupContextElement.getNewElastiGroupOriginalConfig().getId();

      AwsAmiInfrastructure awsAmiInfrastructure = (AwsAmiInfrastructure) spotinstInfraDef.getInfrastructure();

      SpotInstConfig spotInstConfig =
          (SpotInstConfig) settingsService.get(awsAmiInfrastructure.getSpotinstCloudProvider()).getValue();
      AwsConfig awsConfig = (AwsConfig) settingsService.get(awsAmiInfrastructure.getCloudProviderId()).getValue();
      List<EncryptedDataDetail> spotinstEncryption = secretManager.getEncryptionDetails(spotInstConfig);
      List<EncryptedDataDetail> awsEncryption = secretManager.getEncryptionDetails(awsConfig);

      List<Instance> oldInstances =
          spotinstHelperServiceManager.listElastigroupInstances(spotInstConfig, spotinstEncryption, awsConfig,
              awsEncryption, awsAmiInfrastructure.getRegion(), spotinstInfraDef.getAppId(), oldElastigroupId);
      List<Instance> newInstances =
          spotinstHelperServiceManager.listElastigroupInstances(spotInstConfig, spotinstEncryption, awsConfig,
              awsEncryption, awsAmiInfrastructure.getRegion(), spotinstInfraDef.getAppId(), newElastigroupId);

      Set<String> oldInstanceIds = oldInstances.stream().map(Instance::getPrivateIpAddress).collect(Collectors.toSet());
      Set<String> newInstanceIds = newInstances.stream().map(Instance::getPrivateIpAddress).collect(Collectors.toSet());

      Set<String> testNodes = (Set<String>) analysisContext.getTestNodes();
      Set<String> controlNodes = (Set<String>) analysisContext.getControlNodes();

      assertThat(oldInstanceIds).isEqualTo(controlNodes);
      assertThat(newInstanceIds).isEqualTo(testNodes);
    }
  }
}
