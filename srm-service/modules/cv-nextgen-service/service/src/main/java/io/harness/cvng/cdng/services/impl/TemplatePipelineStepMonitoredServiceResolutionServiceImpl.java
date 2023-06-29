/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.cvng.core.utils.FeatureFlagNames.CVNG_MONITORED_SERVICE_DEMO;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.cvng.cdng.VerifyStepConstants;
import io.harness.cvng.cdng.beans.CVNGDeploymentStepInfo;
import io.harness.cvng.cdng.beans.CVNGStepInfo;
import io.harness.cvng.cdng.beans.MonitoredServiceNode;
import io.harness.cvng.cdng.beans.ResolvedCVConfigInfo;
import io.harness.cvng.cdng.beans.ResolvedCVConfigInfo.ResolvedCVConfigInfoBuilder;
import io.harness.cvng.cdng.beans.TemplateMonitoredServiceSpec;
import io.harness.cvng.cdng.services.api.PipelineStepMonitoredServiceResolutionService;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.beans.sidekick.VerificationJobInstanceCleanupSideKickData;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.core.services.api.SideKickService;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.utils.FeatureFlagNames;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
@Slf4j
public class TemplatePipelineStepMonitoredServiceResolutionServiceImpl
    implements PipelineStepMonitoredServiceResolutionService {
  @Inject private Clock clock;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private MetricPackService metricPackService;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private SideKickService sideKickService;

  @Override
  public ResolvedCVConfigInfo fetchAndPersistResolvedCVConfigInfo(
      ServiceEnvironmentParams serviceEnvironmentParams, MonitoredServiceNode monitoredServiceNode) {
    TemplateMonitoredServiceSpec templateMonitoredServiceSpec =
        (TemplateMonitoredServiceSpec) monitoredServiceNode.getSpec();
    String executionIdentifier = generateUuid();
    ResolvedCVConfigInfoBuilder resolvedCVConfigInfoBuilder =
        ResolvedCVConfigInfo.builder()
            .monitoredServiceIdentifier(executionIdentifier)
            .monitoredServiceTemplateIdentifier(
                templateMonitoredServiceSpec.getMonitoredServiceTemplateRef().getValue())
            .monitoredServiceTemplateVersionLabel(templateMonitoredServiceSpec.getVersionLabel());
    populateSourceDataFromTemplate(
        serviceEnvironmentParams, templateMonitoredServiceSpec, resolvedCVConfigInfoBuilder, executionIdentifier);
    return resolvedCVConfigInfoBuilder.build();
  }

  @Override
  public void managePerpetualTasks(ServiceEnvironmentParams serviceEnvironmentParams,
      ResolvedCVConfigInfo resolvedCVConfigInfo, String verificationJobInstanceId) {
    List<ResolvedCVConfigInfo.HealthSourceInfo> healthSources = resolvedCVConfigInfo.getHealthSources();
    if (CollectionUtils.isNotEmpty(healthSources)) {
      List<String> sourceIdentifiersToCleanUp = new ArrayList<>();
      healthSources.forEach(healthSource -> {
        String sourceIdentifier = HealthSourceService.getNameSpacedIdentifier(
            resolvedCVConfigInfo.getMonitoredServiceIdentifier(), healthSource.getIdentifier());
        monitoringSourcePerpetualTaskService.createDeploymentTaskAndPerpetualTaskInSyncForTemplateCV(
            serviceEnvironmentParams.getAccountIdentifier(), serviceEnvironmentParams.getOrgIdentifier(),
            serviceEnvironmentParams.getProjectIdentifier(), healthSource.getConnectorRef(), sourceIdentifier,
            healthSource.isDemoEnabledForAnyCVConfig());
        sourceIdentifiersToCleanUp.add(sourceIdentifier);
      });
      sideKickService.schedule(VerificationJobInstanceCleanupSideKickData.builder()
                                   .verificationJobInstanceIdentifier(verificationJobInstanceId)
                                   .sourceIdentifiers(sourceIdentifiersToCleanUp)
                                   .accountIdentifier(serviceEnvironmentParams.getAccountIdentifier())
                                   .orgIdentifier(serviceEnvironmentParams.getOrgIdentifier())
                                   .projectIdentifier(serviceEnvironmentParams.getProjectIdentifier())
                                   .build(),
          clock.instant().plus(Duration.ofHours(3)));
    }
  }

  @Override
  public List<EntityDetailProtoDTO> getReferredEntities(
      FilterCreationContext filterCreationContext, CVNGStepInfo cvngStepInfo, ProjectParams projectParams) {
    return new ArrayList<>();
  }

  @Override
  public List<EntityDetailProtoDTO> getReferredEntities(
      FilterCreationContext filterCreationContext, CVNGDeploymentStepInfo cvngStepInfo, ProjectParams projectParams) {
    throw new RuntimeException("Template Monitored Service is not supported in analyze deployment step");
  }

  private void populateSourceDataFromTemplate(ServiceEnvironmentParams serviceEnvironmentParams,
      TemplateMonitoredServiceSpec templateMonitoredServiceSpec,
      ResolvedCVConfigInfoBuilder resolvedCVConfigInfoBuilder, String executionIdentifier) {
    MonitoredServiceDTO monitoredServiceDTO = monitoredServiceService.getExpandedMonitoredServiceFromYaml(
        ProjectParams.builder()
            .accountIdentifier(serviceEnvironmentParams.getAccountIdentifier())
            .orgIdentifier(serviceEnvironmentParams.getOrgIdentifier())
            .projectIdentifier(serviceEnvironmentParams.getProjectIdentifier())
            .build(),
        getTemplateYaml(templateMonitoredServiceSpec));
    if (Objects.nonNull(monitoredServiceDTO) && Objects.nonNull(monitoredServiceDTO.getSources())
        && CollectionUtils.isNotEmpty(monitoredServiceDTO.getSources().getHealthSources())) {
      persistTemplateMonitoredService(serviceEnvironmentParams, monitoredServiceDTO);
      populateCvConfigAndHealSourceData(serviceEnvironmentParams, monitoredServiceDTO.getSources().getHealthSources(),
          resolvedCVConfigInfoBuilder, executionIdentifier);
    } else {
      resolvedCVConfigInfoBuilder.cvConfigs(Collections.emptyList()).healthSources(Collections.emptyList());
    }
  }

  private void persistTemplateMonitoredService(
      ServiceEnvironmentParams serviceEnvironmentParams, MonitoredServiceDTO monitoredServiceDTO) {
    try {
      if (featureFlagService.isFeatureFlagEnabled(
              serviceEnvironmentParams.getAccountIdentifier(), FeatureFlagNames.PERSIST_MONITORED_SERVICE_TEMPLATE_STEP)
          && Objects.isNull(monitoredServiceService.getMonitoredService(
              MonitoredServiceParams.builder()
                  .projectIdentifier(serviceEnvironmentParams.getProjectIdentifier())
                  .orgIdentifier(serviceEnvironmentParams.getOrgIdentifier())
                  .accountIdentifier(serviceEnvironmentParams.getAccountIdentifier())
                  .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
                  .build()))) {
        monitoredServiceService.create(serviceEnvironmentParams.getAccountIdentifier(), monitoredServiceDTO);
      }
    } catch (Exception e) {
      log.error("Failed to persist monitored service: " + monitoredServiceDTO.getIdentifier(), e);
    }
  }
  @VisibleForTesting
  protected String getTemplateYaml(TemplateMonitoredServiceSpec templateMonitoredServiceSpec) {
    String monitoredServiceTemplateRef = templateMonitoredServiceSpec.getMonitoredServiceTemplateRef().getValue();
    String versionLabel = templateMonitoredServiceSpec.getVersionLabel();
    JsonNode templateInputsNode = templateMonitoredServiceSpec.getTemplateInputs();
    Map<String, JsonNode> templateMap = new HashMap<>();
    templateMap.put(VerifyStepConstants.TEMPLATE_YAML_KEYS_TEMPLATE_REF, new TextNode(monitoredServiceTemplateRef));
    templateMap.put(VerifyStepConstants.TEMPLATE_YAML_KEYS_VERSION_LABEL, new TextNode(versionLabel));
    templateMap.put(VerifyStepConstants.TEMPLATE_YAML_KEYS_TEMPLATE_INPUTS, templateInputsNode);
    JsonNode templateNode = new ObjectNode(JsonNodeFactory.instance, templateMap);
    Map<String, JsonNode> monitoredServiceMap =
        Collections.singletonMap(VerifyStepConstants.TEMPLATE_YAML_KEYS_TEMPLATE, templateNode);
    JsonNode monitoredServiceNode = new ObjectNode(JsonNodeFactory.instance, monitoredServiceMap);
    Map<String, JsonNode> rootMap =
        Collections.singletonMap(VerifyStepConstants.TEMPLATE_YAML_KEYS_MONITORED_SERVICE, monitoredServiceNode);
    JsonNode rootNode = new ObjectNode(JsonNodeFactory.instance, rootMap);
    JsonNode cleanedRootNode = cleanRootNode(rootNode, "__uuid");
    YamlNode yamlNode = new YamlNode(cleanedRootNode);
    String yaml;
    try {
      yaml = YamlUtils.writeYamlString(new YamlField(yamlNode));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return yaml;
  }

  private void populateCvConfigAndHealSourceData(ServiceEnvironmentParams serviceEnvironmentParams,
      Set<HealthSource> healthSources, ResolvedCVConfigInfoBuilder resolvedCVConfigInfoBuilder,
      String executionIdentifier) {
    List<CVConfig> allCvConfigs = new ArrayList<>();
    List<ResolvedCVConfigInfo.HealthSourceInfo> healthSourceInfoList = new ArrayList<>();
    healthSources.forEach(healthSource -> {
      HealthSource.CVConfigUpdateResult cvConfigUpdateResult = healthSource.getSpec().getCVConfigUpdateResult(
          serviceEnvironmentParams.getAccountIdentifier(), serviceEnvironmentParams.getOrgIdentifier(),
          serviceEnvironmentParams.getProjectIdentifier(), serviceEnvironmentParams.getEnvironmentIdentifier(),
          serviceEnvironmentParams.getServiceIdentifier(), executionIdentifier,
          HealthSourceService.getNameSpacedIdentifier(executionIdentifier, healthSource.getIdentifier()),
          healthSource.getName(), Collections.emptyList(), metricPackService);

      boolean isDemoEnabledForAnyCVConfig = false;

      for (CVConfig cvConfig : cvConfigUpdateResult.getAdded()) {
        cvConfig.setEnabled(true);
        if (cvConfig.isEligibleForDemo()
            && featureFlagService.isFeatureFlagEnabled(
                serviceEnvironmentParams.getAccountIdentifier(), CVNG_MONITORED_SERVICE_DEMO)) {
          isDemoEnabledForAnyCVConfig = true;
          cvConfig.setDemo(true);
        }
      }

      if (CollectionUtils.isNotEmpty(cvConfigUpdateResult.getAdded())) {
        allCvConfigs.addAll(cvConfigUpdateResult.getAdded());
      }

      healthSourceInfoList.add(ResolvedCVConfigInfo.HealthSourceInfo.builder()
                                   .connectorRef(healthSource.getSpec().getConnectorRef())
                                   .demoEnabledForAnyCVConfig(isDemoEnabledForAnyCVConfig)
                                   .identifier(healthSource.getIdentifier())
                                   .build());
    });
    // TODO: Adding this to enable end-end execution. Check if this is really required.
    allCvConfigs.forEach(cvConfig -> {
      cvConfig.setDataSourceName(cvConfig.getType());
      cvConfig.setVerificationType(cvConfig.getVerificationType());
      cvConfig.setUuid(generateUuid());
    });
    resolvedCVConfigInfoBuilder.cvConfigs(allCvConfigs).healthSources(healthSourceInfoList);
  }

  private JsonNode cleanRootNode(JsonNode rootNode, String key) {
    Map<String, JsonNode> map = new HashMap<>();
    rootNode.fieldNames().forEachRemaining(i -> {
      if (!i.equals(key)) {
        switch (rootNode.get(i).getNodeType()) {
          case OBJECT:
            map.put(i, cleanRootNode(rootNode.get(i), key));
            break;
          case MISSING:
          case ARRAY:
            List<JsonNode> cleanedChildren = new ArrayList<>();
            ArrayNode arr = (ArrayNode) rootNode.get(i);
            arr.forEach(c -> {
              if (c.isTextual()) {
                cleanedChildren.add(c);
              } else {
                cleanedChildren.add(cleanRootNode(c, key));
              }
            });
            map.put(i, new ArrayNode(JsonNodeFactory.instance, cleanedChildren));
            break;
          default:
            map.put(i, rootNode.get(i));
        }
      }
    });
    return new ObjectNode(JsonNodeFactory.instance, map);
  }
}
