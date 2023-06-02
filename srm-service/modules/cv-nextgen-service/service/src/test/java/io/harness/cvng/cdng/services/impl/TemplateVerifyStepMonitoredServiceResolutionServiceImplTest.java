/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.cdng.beans.MonitoredServiceNode;
import io.harness.cvng.cdng.beans.MonitoredServiceSpec.MonitoredServiceSpecType;
import io.harness.cvng.cdng.beans.ResolvedCVConfigInfo;
import io.harness.cvng.cdng.beans.TemplateMonitoredServiceSpec;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.beans.sidekick.VerificationJobInstanceCleanupSideKickData;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask.MonitoringSourcePerpetualTaskKeys;
import io.harness.cvng.core.entities.SideKick;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.models.VerificationType;
import io.harness.persistence.HPersistence;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TemplateVerifyStepMonitoredServiceResolutionServiceImplTest extends CvNextGenTestBase {
  @Inject private TemplateVerifyStepMonitoredServiceResolutionServiceImpl templateService;
  @Inject private MetricPackService metricPackService;
  @Inject HPersistence hPersistence;
  @Inject ObjectMapper objectMapper;
  @Inject TemplateVerifyStepMonitoredServiceResolutionServiceImpl templateVerifyStepMonitoredServiceResolutionService;
  private BuilderFactory builderFactory;
  private String accountId;
  private String projectIdentifier;
  private String orgIdentifier;
  private String serviceIdentifier;
  private String envIdentifier;
  private MonitoredServiceNode monitoredServiceNode;
  private TemplateMonitoredServiceSpec templateMonitoredServiceSpec;
  private MonitoredServiceDTO monitoredServiceDTO;
  private ServiceEnvironmentParams serviceEnvironmentParams;
  private MonitoredServiceService mockMonitoredServiceService;

  @Before
  public void setup() throws IllegalAccessException, IOException {
    mockMonitoredServiceService = mock(MonitoredServiceService.class);
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    envIdentifier = builderFactory.getContext().getEnvIdentifier();
    monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    serviceEnvironmentParams = getServiceEnvironmentParams();
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    URL testFile =
        TemplateVerifyStepMonitoredServiceResolutionServiceImplTest.class.getResource("verify-step-with-template.json");
    JsonNode templateInputsNode = objectMapper.readTree(testFile);
    templateMonitoredServiceSpec =
        builderFactory.getTemplateMonitoredServiceSpecBuilder().templateInputs(templateInputsNode).build();
    when(mockMonitoredServiceService.getExpandedMonitoredServiceFromYaml(any(), any())).thenReturn(monitoredServiceDTO);
    monitoredServiceNode = getDefaultMonitoredServiceNode();
    FieldUtils.writeField(templateService, "monitoredServiceService", mockMonitoredServiceService, true);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetMonitoredServiceIdentifier() {
    String actualIdentifier =
        templateService.fetchAndPersistResolvedCVConfigInfo(serviceEnvironmentParams, monitoredServiceNode)
            .getMonitoredServiceIdentifier();
    assertThat(actualIdentifier).isNotBlank();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetMonitoredServiceTemplateIdentifier() {
    String templateIdentifier =
        templateService.fetchAndPersistResolvedCVConfigInfo(serviceEnvironmentParams, monitoredServiceNode)
            .getMonitoredServiceTemplateIdentifier();
    assertThat(templateIdentifier).isNotBlank();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetMonitoredServiceTemplateVersionLabel() {
    String versionLabel =
        templateService.fetchAndPersistResolvedCVConfigInfo(serviceEnvironmentParams, monitoredServiceNode)
            .getMonitoredServiceTemplateVersionLabel();
    assertThat(versionLabel).isNotBlank();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigs() {
    List<CVConfig> actualCvConfigs =
        templateService.fetchAndPersistResolvedCVConfigInfo(serviceEnvironmentParams, monitoredServiceNode)
            .getCvConfigs();
    assertThat(actualCvConfigs).hasSize(1);
    assertThat(actualCvConfigs.get(0).getDataSourceName()).isEqualTo(DataSourceType.APP_DYNAMICS);
    assertThat(actualCvConfigs.get(0).getVerificationType()).isEqualTo(VerificationType.TIME_SERIES);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigs_monitoredServiceDtoDoesNotExist() {
    when(mockMonitoredServiceService.getExpandedMonitoredServiceFromYaml(any(), any())).thenReturn(null);
    List<CVConfig> actualCvConfigs =
        templateService.fetchAndPersistResolvedCVConfigInfo(serviceEnvironmentParams, monitoredServiceNode)
            .getCvConfigs();
    assertThat(actualCvConfigs).hasSize(0);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigs_healthSourcesDoNotExist() {
    monitoredServiceDTO.getSources().setHealthSources(Collections.emptySet());
    List<CVConfig> actualCvConfigs =
        templateService.fetchAndPersistResolvedCVConfigInfo(serviceEnvironmentParams, monitoredServiceNode)
            .getCvConfigs();
    assertThat(actualCvConfigs).hasSize(0);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigs_SourcesDoNotExist() {
    monitoredServiceDTO.setSources(null);
    List<CVConfig> actualCvConfigs =
        templateService.fetchAndPersistResolvedCVConfigInfo(serviceEnvironmentParams, monitoredServiceNode)
            .getCvConfigs();
    assertThat(actualCvConfigs).hasSize(0);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testManagePerpetualTasks_verifyPerpetualTasksGotCreated() {
    ResolvedCVConfigInfo resolvedCVConfigInfo =
        templateService.fetchAndPersistResolvedCVConfigInfo(serviceEnvironmentParams, monitoredServiceNode);
    String verificationJobInstanceId = generateUuid();
    templateService.managePerpetualTasks(serviceEnvironmentParams, resolvedCVConfigInfo, verificationJobInstanceId);
    Query<MonitoringSourcePerpetualTask> query =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class, excludeAuthority)
            .filter(MonitoringSourcePerpetualTaskKeys.accountId, accountId)
            .filter(MonitoringSourcePerpetualTaskKeys.projectIdentifier, projectIdentifier)
            .filter(MonitoringSourcePerpetualTaskKeys.orgIdentifier, orgIdentifier);
    List<MonitoringSourcePerpetualTask> savedPerpetualTasks = query.asList();
    assertThat(savedPerpetualTasks).hasSize(2);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testManagePerpetualTasks_verifySideKickGotCreated() {
    ResolvedCVConfigInfo resolvedCVConfigInfo =
        templateService.fetchAndPersistResolvedCVConfigInfo(serviceEnvironmentParams, monitoredServiceNode);
    String verificationJobInstanceId = generateUuid();
    templateService.managePerpetualTasks(serviceEnvironmentParams, resolvedCVConfigInfo, verificationJobInstanceId);
    Query<SideKick> query = hPersistence.createQuery(SideKick.class, excludeAuthority);
    List<SideKick> savedSideKicks = query.asList();
    assertThat(savedSideKicks).hasSize(1);
    assertThat(savedSideKicks.get(0).getSideKickData().getType())
        .isEqualTo(SideKick.Type.VERIFICATION_JOB_INSTANCE_CLEANUP);
    VerificationJobInstanceCleanupSideKickData sideKickData =
        (VerificationJobInstanceCleanupSideKickData) savedSideKicks.get(0).getSideKickData();
    assertThat(sideKickData.getSourceIdentifiers().size()).isEqualTo(resolvedCVConfigInfo.getHealthSources().size());
    assertThat(sideKickData.getVerificationJobInstanceIdentifier()).isEqualTo(verificationJobInstanceId);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testManagePerpetualTasks_noHealthSources() {
    monitoredServiceDTO.setSources(null);
    ResolvedCVConfigInfo resolvedCVConfigInfo =
        templateService.fetchAndPersistResolvedCVConfigInfo(serviceEnvironmentParams, monitoredServiceNode);
    String verificationJobInstanceId = generateUuid();
    templateService.managePerpetualTasks(serviceEnvironmentParams, resolvedCVConfigInfo, verificationJobInstanceId);
    List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasks =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class, excludeAuthority)
            .filter(MonitoringSourcePerpetualTaskKeys.accountId, accountId)
            .filter(MonitoringSourcePerpetualTaskKeys.projectIdentifier, projectIdentifier)
            .filter(MonitoringSourcePerpetualTaskKeys.orgIdentifier, orgIdentifier)
            .asList();
    List<SideKick> sideKicks = hPersistence.createQuery(SideKick.class, excludeAuthority).asList();
    assertThat(monitoringSourcePerpetualTasks).hasSize(0);
    assertThat(sideKicks).hasSize(0);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testGetTemplateYaml() throws IOException {
    URL testFile = TemplateVerifyStepMonitoredServiceResolutionServiceImplTest.class.getResource(
        "verify-step-with-template-1.json");
    JsonNode templateInputsNode = objectMapper.readTree(testFile);
    ParameterField<String> parameterField = io.harness.pms.yaml.ParameterField.createValueField("abc");
    TemplateMonitoredServiceSpec templateMonitoredServiceSpec = TemplateMonitoredServiceSpec.builder()
                                                                    .monitoredServiceTemplateRef(parameterField)
                                                                    .versionLabel("1")
                                                                    .templateInputs(templateInputsNode)
                                                                    .build();
    String expectedResponse = "---\n"
        + "monitoredService:\n"
        + "  template:\n"
        + "    templateRef: abc\n"
        + "    versionLabel: \"1\"\n"
        + "    templateInputs:\n"
        + "      sources:\n"
        + "        healthSources:\n"
        + "          - identifier: datadog\n"
        + "            type: DatadogLog\n"
        + "            spec:\n"
        + "              queries:\n"
        + "                - identifier: Datadog_Logs_Query\n"
        + "                  indexes:\n"
        + "                    - abc\n"
        + "                  query: abc\n"
        + "                  serviceInstanceIdentifier: Instance\n"
        + "      type: Application\n";
    String response = templateVerifyStepMonitoredServiceResolutionService.getTemplateYaml(templateMonitoredServiceSpec);
    assertThat(expectedResponse).isEqualTo(response);
  }

  private MonitoredServiceNode getDefaultMonitoredServiceNode() {
    MonitoredServiceNode monitoredServiceNode = new MonitoredServiceNode();
    monitoredServiceNode.setSpec(templateMonitoredServiceSpec);
    monitoredServiceNode.setType(MonitoredServiceSpecType.TEMPLATE.name());
    return monitoredServiceNode;
  }

  private ServiceEnvironmentParams getServiceEnvironmentParams() {
    return ServiceEnvironmentParams.builder()
        .serviceIdentifier(serviceIdentifier)
        .environmentIdentifier(envIdentifier)
        .orgIdentifier(orgIdentifier)
        .accountIdentifier(accountId)
        .projectIdentifier(projectIdentifier)
        .build();
  }
}
