/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.seeddata;

import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.seeddata.SampleDataProviderConstants.HARNESS_SAMPLE_APP;
import static io.harness.seeddata.SampleDataProviderConstants.K8S_CANARY_WORKFLOW_NAME;
import static io.harness.seeddata.SampleDataProviderConstants.K8S_INFRA_NAME;
import static io.harness.seeddata.SampleDataProviderConstants.K8S_PIPELINE_NAME;
import static io.harness.seeddata.SampleDataProviderConstants.K8S_PROD_ENVIRONMENT;
import static io.harness.seeddata.SampleDataProviderConstants.K8S_QA_ENVIRONMENT;
import static io.harness.seeddata.SampleDataProviderConstants.K8S_ROLLING_WORKFLOW_NAME;
import static io.harness.seeddata.SampleDataProviderConstants.K8S_SERVICE_NAME;

import static software.wings.beans.Account.Builder.anAccount;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowService;
import software.wings.utils.WingsTestConstants;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SampleDataProviderServiceTest extends WingsBaseTest {
  @Inject private HPersistence persistence;

  @Inject private SampleDataProviderServiceImpl sampleDataProviderService;
  @Inject private SettingsService settingsService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private EnvironmentService environmentService;
  @Inject private WorkflowService workflowService;
  @Inject private PipelineService pipelineService;
  @Inject private AppService appService;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCreateSampleAppWithInfraDefinitions() {
    Account account =
        anAccount().withAccountName(WingsTestConstants.ACCOUNT_NAME).withUuid(WingsTestConstants.ACCOUNT_ID).build();
    String accountId = persistence.save(account);

    assertThat(accountId).isNotNull();

    sampleDataProviderService.createK8sV2SampleApp(account);

    final Application app = appService.getAppByName(account.getUuid(), SampleDataProviderConstants.HARNESS_SAMPLE_APP);
    assertThat(app).isNotNull();

    // Verify the Kube cluster cloud provider
    assertThat(settingsService.getSettingAttributeByName(
                   account.getUuid(), SampleDataProviderConstants.K8S_CLOUD_PROVIDER_NAME))
        .isNotNull();
    // Verify the ConnectorDisconnectHandler created
    assertThat(settingsService.getSettingAttributeByName(
                   account.getUuid(), SampleDataProviderConstants.HARNESS_DOCKER_HUB_CONNECTOR))
        .isNotNull();
    // Verify the app
    assertThat(appService.getAppByName(account.getUuid(), HARNESS_SAMPLE_APP)).isNotNull();
    // Verify  service
    assertThat(serviceResourceService.getServiceByName(app.getUuid(), K8S_SERVICE_NAME)).isNotNull();
    // Verify environment
    Environment qaEnv = environmentService.getEnvironmentByName(app.getUuid(), K8S_QA_ENVIRONMENT);
    assertThat(qaEnv).isNotNull();
    Environment prodEnv = environmentService.getEnvironmentByName(app.getUuid(), K8S_PROD_ENVIRONMENT);
    assertThat(prodEnv).isNotNull();

    // Verify infra definition
    assertThat(infrastructureDefinitionService.getInfraDefByName(app.getAppId(), qaEnv.getUuid(), K8S_INFRA_NAME))
        .isNotNull();

    assertThat(infrastructureDefinitionService.getInfraDefByName(app.getAppId(), prodEnv.getUuid(), K8S_INFRA_NAME))
        .isNotNull();

    // Verify Basic workflow
    assertThat(workflowService.readWorkflowByName(app.getAppId(), K8S_ROLLING_WORKFLOW_NAME)).isNotNull();

    // Verify Canary workflow
    assertThat(workflowService.readWorkflowByName(app.getAppId(), K8S_CANARY_WORKFLOW_NAME)).isNotNull();

    // Verify pipeline
    assertThat(pipelineService.getPipelineByName(app.getAppId(), K8S_PIPELINE_NAME)).isNotNull();
  }
}
