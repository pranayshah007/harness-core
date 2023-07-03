/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.cdng.beans.CVNGDeploymentStepInfo;
import io.harness.cvng.cdng.beans.ConfiguredMonitoredServiceSpec;
import io.harness.cvng.cdng.beans.DefaultAndConfiguredMonitoredServiceNode;
import io.harness.cvng.cdng.beans.MonitoredServiceSpecType;
import io.harness.cvng.cdng.services.api.CDStageMetaDataService;
import io.harness.cvng.cdng.services.api.PipelineStepMonitoredServiceResolutionService;
import io.harness.cvng.core.beans.StepSpecTypeConstants;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.ng.core.dto.CDStageMetaDataDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext.FilterCreationContextBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CVNGAnalyzeDeploymentStepFilterJsonCreatorTest extends CvNextGenTestBase {
  private static final List<String> YAML_FILE_PATHS = List.of("pipeline/pipeline-with-analyze-deployment.yaml");
  private static final List<String> CONFIGURED_YAML_FILE_PATHS =
      List.of("pipeline/pipeline-with-analyze-deployment-configured-monitored-service.yaml");

  @Inject private CVNGAnalyzeDeploymentStepFilterJsonCreator cvngAnalyzeDeploymentStepFilterJsonCreator;
  @Inject
  private Map<MonitoredServiceSpecType, PipelineStepMonitoredServiceResolutionService> verifyStepCvConfigServiceMap;
  @Inject private MetricPackService metricPackService;
  @Mock private CDStageMetaDataService cdStageMetaDataService;

  @Inject private MonitoredServiceService monitoredServiceService;

  private BuilderFactory builderFactory;
  private String accountId;
  private String projectIdentifier;
  private String orgIdentifier;
  private String serviceIdentifier;
  private String envIdentifier;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    envIdentifier = builderFactory.getContext().getEnvIdentifier();
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);

    ResponseDTO<CDStageMetaDataDTO> responseDTO = ResponseDTO.newResponse(
        CDStageMetaDataDTO.builder().serviceRef(serviceIdentifier).environmentRef(envIdentifier).build());
    when(cdStageMetaDataService.getServiceAndEnvironmentRef(any())).thenReturn(responseDTO);

    FieldUtils.writeField(verifyStepCvConfigServiceMap.get(MonitoredServiceSpecType.DEFAULT), "cdStageMetaDataService",
        cdStageMetaDataService, true);
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testHandleNode_monitoredServiceDoesNotExist() {
    ResponseDTO<CDStageMetaDataDTO> responseDTO =
        ResponseDTO.newResponse(CDStageMetaDataDTO.builder()
                                    .serviceEnvRef(CDStageMetaDataDTO.ServiceEnvRef.builder()
                                                       .environmentRef(builderFactory.getContext().getEnvIdentifier())
                                                       .serviceRef(builderFactory.getContext().getServiceIdentifier())
                                                       .build())
                                    .build());
    when(cdStageMetaDataService.getServiceAndEnvironmentRef(any())).thenReturn(responseDTO);
    YAML_FILE_PATHS.forEach(yamlFilePath
        -> assertThatThrownBy(()
                                  -> cvngAnalyzeDeploymentStepFilterJsonCreator.handleNode(
                                      FilterCreationContext.builder()
                                          .currentField(getDeploymentStepYamlField(yamlFilePath))
                                          .setupMetadata(SetupMetadata.newBuilder()
                                                             .setAccountId(accountId)
                                                             .setOrgId(orgIdentifier)
                                                             .setProjectId(projectIdentifier)
                                                             .build())
                                          .build(),
                                      StepElementConfig.builder()
                                          .stepSpecType(builderFactory.cvngDeploymentStepInfoBuilder().build())
                                          .build()))
               .isInstanceOf(NullPointerException.class)
               .hasMessage(
                   "MonitoredService does not exist for service %s and env %s", serviceIdentifier, envIdentifier));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testHandleNode_valid() throws IOException {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ResponseDTO<CDStageMetaDataDTO> responseDTO =
        ResponseDTO.newResponse(CDStageMetaDataDTO.builder()
                                    .serviceEnvRef(CDStageMetaDataDTO.ServiceEnvRef.builder()
                                                       .environmentRef(monitoredServiceDTO.getEnvironmentRef())
                                                       .serviceRef(monitoredServiceDTO.getServiceRef())
                                                       .build())
                                    .build());
    when(cdStageMetaDataService.getServiceAndEnvironmentRef(any())).thenReturn(responseDTO);
    for (String yamlFilePath : YAML_FILE_PATHS) {
      FilterCreationResponse filterCreationResponse = cvngAnalyzeDeploymentStepFilterJsonCreator.handleNode(
          FilterCreationContext.builder()
              .setupMetadata(SetupMetadata.newBuilder()
                                 .setAccountId(accountId)
                                 .setOrgId(orgIdentifier)
                                 .setProjectId(projectIdentifier)
                                 .build())
              .currentField(getDeploymentStepYamlField(yamlFilePath))
              .build(),
          StepElementConfig.builder().stepSpecType(builderFactory.cvngDeploymentStepInfoBuilder().build()).build());
      assertThat(filterCreationResponse.getReferredEntities()).hasSize(1);
      assertThat(filterCreationResponse.getReferredEntities().get(0).getIdentifierRef().getIdentifier().getValue())
          .isEqualTo(BuilderFactory.CONNECTOR_IDENTIFIER);
      assertThat(
          filterCreationResponse.getReferredEntities().get(0).getIdentifierRef().getProjectIdentifier().getValue())
          .isEqualTo(projectIdentifier);
      assertThat(filterCreationResponse.getReferredEntities().get(0).getIdentifierRef().getOrgIdentifier().getValue())
          .isEqualTo(orgIdentifier);
      assertThat(
          filterCreationResponse.getReferredEntities().get(0).getIdentifierRef().getAccountIdentifier().getValue())
          .isEqualTo(accountId);
    }
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testHandleNode_whenServiceOrEnvIsRuntimeOrExpression() throws IOException {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    serviceIdentifier = "<+input>";
    ResponseDTO<CDStageMetaDataDTO> responseDTO = ResponseDTO.newResponse(
        CDStageMetaDataDTO.builder().serviceRef(serviceIdentifier).environmentRef(envIdentifier).build());
    when(cdStageMetaDataService.getServiceAndEnvironmentRef(any())).thenReturn(responseDTO);
    for (String yamlFilePath : YAML_FILE_PATHS) {
      YamlField yamlField = getDeploymentStepYamlField(yamlFilePath, serviceIdentifier, envIdentifier);
      FilterCreationContextBuilder filterCreationContextBuilder =
          FilterCreationContext.builder().setupMetadata(SetupMetadata.newBuilder()
                                                            .setAccountId(accountId)
                                                            .setOrgId(orgIdentifier)
                                                            .setProjectId(projectIdentifier)
                                                            .build());
      FilterCreationResponse filterCreationResponse = cvngAnalyzeDeploymentStepFilterJsonCreator.handleNode(
          filterCreationContextBuilder.currentField(yamlField).build(),
          StepElementConfig.builder().stepSpecType(builderFactory.cvngDeploymentStepInfoBuilder().build()).build());

      assertThat(filterCreationResponse.getReferredEntities()).isEmpty();

      filterCreationResponse = cvngAnalyzeDeploymentStepFilterJsonCreator.handleNode(
          filterCreationContextBuilder
              .currentField(
                  getDeploymentStepYamlField(yamlFilePath, "verification", "<+serviceConfig.artifacts.primary.tag>"))
              .build(),
          StepElementConfig.builder().stepSpecType(builderFactory.cvngDeploymentStepInfoBuilder().build()).build());
      assertThat(filterCreationResponse.getReferredEntities()).isEmpty();
    }
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testHandleNode_nullDurationField() {
    for (String yamlFilePath : YAML_FILE_PATHS) {
      assertThatThrownBy(
          ()
              -> cvngAnalyzeDeploymentStepFilterJsonCreator.handleNode(
                  FilterCreationContext.builder()
                      .setupMetadata(SetupMetadata.newBuilder()
                                         .setAccountId(accountId)
                                         .setOrgId(orgIdentifier)
                                         .setProjectId(projectIdentifier)
                                         .build())
                      .currentField(getDeploymentStepYamlField(yamlFilePath))
                      .build(),
                  StepElementConfig.builder()
                      .stepSpecType(builderFactory.cvngDeploymentStepInfoBuilder().duration(null).build())
                      .build()))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testHandleNode_durationIsExpression() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ResponseDTO<CDStageMetaDataDTO> responseDTO =
        ResponseDTO.newResponse(CDStageMetaDataDTO.builder()
                                    .serviceEnvRef(CDStageMetaDataDTO.ServiceEnvRef.builder()
                                                       .environmentRef(monitoredServiceDTO.getEnvironmentRef())
                                                       .serviceRef(monitoredServiceDTO.getServiceRef())
                                                       .build())
                                    .build());
    when(cdStageMetaDataService.getServiceAndEnvironmentRef(any())).thenReturn(responseDTO);
    for (String yamlFilePath : YAML_FILE_PATHS) {
      FilterCreationResponse filterCreationResponse = cvngAnalyzeDeploymentStepFilterJsonCreator.handleNode(
          FilterCreationContext.builder()
              .setupMetadata(SetupMetadata.newBuilder()
                                 .setAccountId(accountId)
                                 .setOrgId(orgIdentifier)
                                 .setProjectId(projectIdentifier)
                                 .build())
              .currentField(getDeploymentStepYamlField(yamlFilePath))
              .build(),
          StepElementConfig.builder()
              .stepSpecType(builderFactory.cvngDeploymentStepInfoBuilder()
                                .duration(ParameterField.createExpressionField(true, "<+step.input>", null, true))
                                .build())
              .build());
      assertThat(filterCreationResponse.getReferredEntities()).hasSize(1);
    }
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testHandleNode_configuredMonitoredServiceDoesNotExist() {
    String monitoredServiceIdentifier = randomAlphabetic(10);
    ParameterField<String> monitoredServiceRef = new ParameterField<>();
    monitoredServiceRef.setValue(monitoredServiceIdentifier);
    DefaultAndConfiguredMonitoredServiceNode monitoredServiceNode =
        DefaultAndConfiguredMonitoredServiceNode.builder()
            .spec(ConfiguredMonitoredServiceSpec.builder().monitoredServiceRef(monitoredServiceRef).build())
            .type("Configured")
            .build();
    CVNGDeploymentStepInfo cvngDeploymentStepInfo = builderFactory.cvngDeploymentStepInfoBuilder().build();
    cvngDeploymentStepInfo.setMonitoredService(monitoredServiceNode);
    CONFIGURED_YAML_FILE_PATHS.forEach(yamlFilePath
        -> assertThatThrownBy(()
                                  -> cvngAnalyzeDeploymentStepFilterJsonCreator.handleNode(
                                      FilterCreationContext.builder()
                                          .currentField(getDeploymentStepYamlField(yamlFilePath))
                                          .setupMetadata(SetupMetadata.newBuilder()
                                                             .setAccountId(accountId)
                                                             .setOrgId(orgIdentifier)
                                                             .setProjectId(projectIdentifier)
                                                             .build())
                                          .build(),
                                      StepElementConfig.builder().stepSpecType(cvngDeploymentStepInfo).build()))
               .isInstanceOf(NullPointerException.class)
               .hasMessage("MonitoredService does not exist for identifier %s", monitoredServiceIdentifier));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testHandleNode_configuredMonitoredServiceInputParam() {
    ParameterField<String> monitoredServiceRef =
        ParameterField.createExpressionField(true, "<+step.input>", null, true);
    DefaultAndConfiguredMonitoredServiceNode monitoredServiceNode =
        DefaultAndConfiguredMonitoredServiceNode.builder()
            .spec(ConfiguredMonitoredServiceSpec.builder().monitoredServiceRef(monitoredServiceRef).build())
            .type("Configured")
            .build();
    CVNGDeploymentStepInfo cvngDeploymentStepInfo = builderFactory.cvngDeploymentStepInfoBuilder().build();
    cvngDeploymentStepInfo.setMonitoredService(monitoredServiceNode);
    CONFIGURED_YAML_FILE_PATHS.forEach(yamlFilePath -> {
      FilterCreationResponse filterCreationResponse = cvngAnalyzeDeploymentStepFilterJsonCreator.handleNode(
          FilterCreationContext.builder()
              .currentField(getDeploymentStepYamlField(yamlFilePath))
              .setupMetadata(SetupMetadata.newBuilder()
                                 .setAccountId(accountId)
                                 .setOrgId(orgIdentifier)
                                 .setProjectId(projectIdentifier)
                                 .build())
              .build(),
          StepElementConfig.builder().stepSpecType(cvngDeploymentStepInfo).build());
      assertThat(filterCreationResponse.getReferredEntities()).isEmpty();
    });
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testHandleNode_validConfiguredMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ParameterField<String> monitoredServiceRef = new ParameterField<>();
    monitoredServiceRef.setValue(monitoredServiceDTO.getIdentifier());
    DefaultAndConfiguredMonitoredServiceNode monitoredServiceNode =
        DefaultAndConfiguredMonitoredServiceNode.builder()
            .spec(ConfiguredMonitoredServiceSpec.builder().monitoredServiceRef(monitoredServiceRef).build())
            .type("Configured")
            .build();
    CVNGDeploymentStepInfo cvngDeploymentStepInfo = builderFactory.cvngDeploymentStepInfoBuilder().build();
    cvngDeploymentStepInfo.setMonitoredService(monitoredServiceNode);
    for (String yamlFilePath : CONFIGURED_YAML_FILE_PATHS) {
      FilterCreationResponse filterCreationResponse = cvngAnalyzeDeploymentStepFilterJsonCreator.handleNode(
          FilterCreationContext.builder()
              .setupMetadata(SetupMetadata.newBuilder()
                                 .setAccountId(accountId)
                                 .setOrgId(orgIdentifier)
                                 .setProjectId(projectIdentifier)
                                 .build())
              .currentField(getDeploymentStepYamlField(yamlFilePath))
              .build(),
          StepElementConfig.builder().stepSpecType(cvngDeploymentStepInfo).build());
      assertThat(filterCreationResponse.getReferredEntities()).hasSize(1);
      assertThat(filterCreationResponse.getReferredEntities().get(0).getIdentifierRef().getIdentifier().getValue())
          .isEqualTo(BuilderFactory.CONNECTOR_IDENTIFIER);
      assertThat(
          filterCreationResponse.getReferredEntities().get(0).getIdentifierRef().getProjectIdentifier().getValue())
          .isEqualTo(projectIdentifier);
      assertThat(filterCreationResponse.getReferredEntities().get(0).getIdentifierRef().getOrgIdentifier().getValue())
          .isEqualTo(orgIdentifier);
      assertThat(
          filterCreationResponse.getReferredEntities().get(0).getIdentifierRef().getAccountIdentifier().getValue())
          .isEqualTo(accountId);
    }
  }

  @SneakyThrows
  public YamlField getDeploymentStepYamlField(String yamlFilePath) {
    return getDeploymentStepYamlField(yamlFilePath, serviceIdentifier, envIdentifier);
  }

  public YamlField getDeploymentStepYamlField(String yamlFilePath, String serviceRef, String envRef)
      throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource(yamlFilePath);
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    yamlContent = yamlContent.replace("$serviceRef", serviceRef);
    yamlContent = yamlContent.replace("$environmentRef", envRef);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    return getDeploymentStep(yamlField);
  }

  private YamlField getDeploymentStep(YamlField yamlField) {
    if (StepSpecTypeConstants.ANALYZE_DEPLOYMENT_IMPACT.equals(yamlField.getNode().getType())) {
      return yamlField;
    } else {
      for (YamlField child : yamlField.getNode().fields()) {
        YamlField result = getDeploymentStep(child);
        if (result != null) {
          return result;
        }
      }

      if (yamlField.getNode().isArray()) {
        for (YamlNode child : yamlField.getNode().asArray()) {
          YamlField result = getDeploymentStep(new YamlField(child));
          if (result != null) {
            return result;
          }
        }
      }
      return null;
    }
  }
}
