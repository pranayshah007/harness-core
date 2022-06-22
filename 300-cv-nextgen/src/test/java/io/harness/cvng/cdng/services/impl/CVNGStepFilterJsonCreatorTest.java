/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.cdng.beans.CVNGStepInfo;
import io.harness.cvng.cdng.beans.CVNGStepType;
import io.harness.cvng.cdng.beans.ConfiguredMonitoredServiceSpec;
import io.harness.cvng.cdng.beans.MonitoredServiceNode;
import io.harness.cvng.cdng.beans.TestVerificationJobSpec;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.monitoredService.ChangeSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class CVNGStepFilterJsonCreatorTest extends CvNextGenTestBase {
  private static final List<String> YAML_FILE_PATHS = Arrays.asList("pipeline/pipeline-with-verify.yaml",
      "pipeline/pipeline-canary-with-verify.yaml", "pipeline/pipeline-service-propagation-with-verify.yaml");
  private static final List<String> CONFIGURED_YAML_FILE_PATHS =
      Arrays.asList("pipeline/pipeline-with-verify-configured-monitored-service.yaml");
  @Inject private CVNGStepFilterJsonCreator cvngStepFilterJsonCreator;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private ChangeSourceService changeSourceService;
  @Inject private MetricPackService metricPackService;
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

    FieldUtils.writeField(changeSourceService, "changeSourceUpdateHandlerMap", new HashMap<>(), true);
    FieldUtils.writeField(monitoredServiceService, "changeSourceService", changeSourceService, true);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testHandleNode_monitoredServiceDoesNotExist() {
    YAML_FILE_PATHS.forEach(yamlFilePath
        -> assertThatThrownBy(
            ()
                -> cvngStepFilterJsonCreator.handleNode(FilterCreationContext.builder()
                                                            .currentField(getVerifyStepYamlField(yamlFilePath))
                                                            .setupMetadata(SetupMetadata.newBuilder()
                                                                               .setAccountId(accountId)
                                                                               .setOrgId(orgIdentifier)
                                                                               .setProjectId(projectIdentifier)
                                                                               .build())
                                                            .build(),
                    StepElementConfig.builder().stepSpecType(builderFactory.cvngStepInfoBuilder().build()).build()))
               .isInstanceOf(NullPointerException.class)
               .hasMessage(
                   "MonitoredService does not exist for service %s and env %s", serviceIdentifier, envIdentifier));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testHandleNode_valid() throws IOException {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    for (String yamlFilePath : YAML_FILE_PATHS) {
      FilterCreationResponse filterCreationResponse =
          cvngStepFilterJsonCreator.handleNode(FilterCreationContext.builder()
                                                   .setupMetadata(SetupMetadata.newBuilder()
                                                                      .setAccountId(accountId)
                                                                      .setOrgId(orgIdentifier)
                                                                      .setProjectId(projectIdentifier)
                                                                      .build())
                                                   .currentField(getVerifyStepYamlField(yamlFilePath))
                                                   .build(),
              StepElementConfig.builder().stepSpecType(builderFactory.cvngStepInfoBuilder().build()).build());
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
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testHandleNode_whenServiceOrEnvIsRuntimeOrExpression() throws IOException {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    for (String yamlFilePath : YAML_FILE_PATHS) {
      YamlField yamlField = getVerifyStepYamlField(yamlFilePath, "<+input>", "Prod");
      FilterCreationContextBuilder filterCreationContextBuilder =
          FilterCreationContext.builder().setupMetadata(SetupMetadata.newBuilder()
                                                            .setAccountId(accountId)
                                                            .setOrgId(orgIdentifier)
                                                            .setProjectId(projectIdentifier)
                                                            .build());
      FilterCreationResponse filterCreationResponse =
          cvngStepFilterJsonCreator.handleNode(filterCreationContextBuilder.currentField(yamlField).build(),
              StepElementConfig.builder().stepSpecType(builderFactory.cvngStepInfoBuilder().build()).build());

      assertThat(filterCreationResponse.getReferredEntities()).isEmpty();

      filterCreationResponse = cvngStepFilterJsonCreator.handleNode(
          filterCreationContextBuilder
              .currentField(
                  getVerifyStepYamlField(yamlFilePath, "verification", "<+serviceConfig.artifacts.primary.tag>"))
              .build(),
          StepElementConfig.builder().stepSpecType(builderFactory.cvngStepInfoBuilder().build()).build());
      assertThat(filterCreationResponse.getReferredEntities()).isEmpty();
    }
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testHandleNode_invalidDurationField() {
    for (String yamlFilePath : YAML_FILE_PATHS) {
      assertThatThrownBy(
          ()
              -> cvngStepFilterJsonCreator.handleNode(FilterCreationContext.builder()
                                                          .setupMetadata(SetupMetadata.newBuilder()
                                                                             .setAccountId(accountId)
                                                                             .setOrgId(orgIdentifier)
                                                                             .setProjectId(projectIdentifier)
                                                                             .build())
                                                          .currentField(getVerifyStepYamlField(yamlFilePath))
                                                          .build(),
                  StepElementConfig.builder()
                      .stepSpecType(builderFactory.cvngStepInfoBuilder()
                                        .spec(TestVerificationJobSpec.builder()
                                                  .duration(ParameterField.createValueField("5mm"))
                                                  .deploymentTag(ParameterField.createValueField("build#1"))
                                                  .sensitivity(ParameterField.createValueField("Low"))
                                                  .build())
                                        .build())
                      .build()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("can not parse duration please check format for duration., ex: 5m, 10m etc.");
    }
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testHandleNode_durationIsExpression() throws IOException {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    for (String yamlFilePath : YAML_FILE_PATHS) {
      FilterCreationResponse filterCreationResponse =
          cvngStepFilterJsonCreator.handleNode(FilterCreationContext.builder()
                                                   .setupMetadata(SetupMetadata.newBuilder()
                                                                      .setAccountId(accountId)
                                                                      .setOrgId(orgIdentifier)
                                                                      .setProjectId(projectIdentifier)
                                                                      .build())
                                                   .currentField(getVerifyStepYamlField(yamlFilePath))
                                                   .build(),
              StepElementConfig.builder()
                  .stepSpecType(
                      builderFactory.cvngStepInfoBuilder()
                          .spec(TestVerificationJobSpec.builder()
                                    .duration(ParameterField.createExpressionField(true, "<+step.input>", null, true))
                                    .deploymentTag(ParameterField.createValueField("build#1"))
                                    .sensitivity(ParameterField.createValueField("Low"))
                                    .build())
                          .build())
                  .build());
      assertThat(filterCreationResponse.getReferredEntities()).hasSize(1);
    }
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testHandleNode_configuredMonitoredServiceDoesNotExist() {
    String monitoredServiceIdentifier = randomAlphabetic(10);
    ParameterField<String> monitoredServiceRef = new ParameterField<>();
    monitoredServiceRef.setValue(monitoredServiceIdentifier);
    MonitoredServiceNode monitoredServiceNode =
        MonitoredServiceNode.builder()
            .spec(ConfiguredMonitoredServiceSpec.builder().monitoredServiceRef(monitoredServiceRef).build())
            .type("Configured")
            .build();
    CVNGStepInfo cvngStepInfo = builderFactory.cvngStepInfoBuilder().build();
    cvngStepInfo.setMonitoredService(monitoredServiceNode);
    CONFIGURED_YAML_FILE_PATHS.forEach(yamlFilePath
        -> assertThatThrownBy(
            ()
                -> cvngStepFilterJsonCreator.handleNode(FilterCreationContext.builder()
                                                            .currentField(getVerifyStepYamlField(yamlFilePath))
                                                            .setupMetadata(SetupMetadata.newBuilder()
                                                                               .setAccountId(accountId)
                                                                               .setOrgId(orgIdentifier)
                                                                               .setProjectId(projectIdentifier)
                                                                               .build())
                                                            .build(),
                    StepElementConfig.builder().stepSpecType(cvngStepInfo).build()))
               .isInstanceOf(NullPointerException.class)
               .hasMessage("MonitoredService does not exist for identifier %s", monitoredServiceIdentifier));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testHandleNode_validConfiguredMonitoredService() throws IOException {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ParameterField<String> monitoredServiceRef = new ParameterField<>();
    monitoredServiceRef.setValue(monitoredServiceDTO.getIdentifier());
    MonitoredServiceNode monitoredServiceNode =
        MonitoredServiceNode.builder()
            .spec(ConfiguredMonitoredServiceSpec.builder().monitoredServiceRef(monitoredServiceRef).build())
            .type("Configured")
            .build();
    CVNGStepInfo cvngStepInfo = builderFactory.cvngStepInfoBuilder().build();
    cvngStepInfo.setMonitoredService(monitoredServiceNode);
    for (String yamlFilePath : CONFIGURED_YAML_FILE_PATHS) {
      FilterCreationResponse filterCreationResponse =
          cvngStepFilterJsonCreator.handleNode(FilterCreationContext.builder()
                                                   .setupMetadata(SetupMetadata.newBuilder()
                                                                      .setAccountId(accountId)
                                                                      .setOrgId(orgIdentifier)
                                                                      .setProjectId(projectIdentifier)
                                                                      .build())
                                                   .currentField(getVerifyStepYamlField(yamlFilePath))
                                                   .build(),
              StepElementConfig.builder().stepSpecType(cvngStepInfo).build());
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

  public YamlField getVerifyStepYamlField(String yamlFilePath) throws IOException {
    return getVerifyStepYamlField(yamlFilePath, serviceIdentifier, envIdentifier);
  }

  public YamlField getVerifyStepYamlField(String yamlFilePath, String serviceRef, String envRef) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource(yamlFilePath);
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    yamlContent = yamlContent.replace("$serviceRef", serviceRef);
    yamlContent = yamlContent.replace("$environmentRef", envRef);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    return getVerifyStep(yamlField);
  }

  private YamlField getVerifyStep(YamlField yamlField) {
    if (CVNGStepType.CVNG_VERIFY.getDisplayName().equals(yamlField.getNode().getType())) {
      return yamlField;
    } else {
      for (YamlField child : yamlField.getNode().fields()) {
        YamlField result = getVerifyStep(child);
        if (result != null) {
          return result;
        }
      }

      if (yamlField.getNode().isArray()) {
        for (YamlNode child : yamlField.getNode().asArray()) {
          YamlField result = getVerifyStep(new YamlField(child));
          if (result != null) {
            return result;
          }
        }
      }
      return null;
    }
  }
}
