/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.pipeline.helpers.ExecutionStrategyTemplates.SSH_WINRM_BASIC_SH_FTL_V2;
import static io.harness.cdng.pipeline.helpers.ExecutionStrategyTemplates.SSH_WINRM_CANARY_SH_FTL_V2;
import static io.harness.cdng.pipeline.helpers.ExecutionStrategyTemplates.SSH_WINRM_ROLLING_SH_FTL_V2;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.NGInstanceUnitType;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.core.template.refresh.YamlFullRefreshResponseDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.steps.matrix.StrategyParameters;
import io.harness.template.remote.TemplateResourceClient;

import software.wings.utils.ArtifactType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;

@Slf4j
@OwnedBy(CDP)
public class CDNGPipelineExecutionStrategyHelperV2 {
  private static String failureStrategiesSnippet;
  private final CDNGPipelineExecutionStrategyHelper cdngPipelineExecutionStrategyHelper;
  private final TemplateResourceClient templateResourceClient;

  static {
    try {
      loadStaticScriptSnippets(Thread.currentThread().getContextClassLoader());
    } catch (IOException e) {
      throw new GeneralException("Error initializing Execution Strategy snippets");
    }
  }

  @Inject
  public CDNGPipelineExecutionStrategyHelperV2(CDNGPipelineExecutionStrategyHelper cdngPipelineExecutionStrategyHelper,
      TemplateResourceClient templateResourceClient) {
    this.cdngPipelineExecutionStrategyHelper = cdngPipelineExecutionStrategyHelper;
    this.templateResourceClient = templateResourceClient;
  }

  private static void loadStaticScriptSnippets(ClassLoader classLoader) throws IOException {
    if (isEmpty(failureStrategiesSnippet)) {
      failureStrategiesSnippet = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource("snippets/Pipelines/execution/default-failure-strategies.yaml")),
          StandardCharsets.UTF_8);
    }
  }

  public String generateCanaryYaml(String accountIdentifier, ServiceDefinitionType serviceDefinitionType,
      boolean includeVerify, StrategyParameters strategyParameters) throws IOException {
    try {
      return generateSshWinRmCanaryYaml(accountIdentifier, serviceDefinitionType, strategyParameters, includeVerify);
    } catch (Exception e) {
      log.error("Error occurred while generating execution yaml from template", e);
      return cdngPipelineExecutionStrategyHelper.generateSshWinRmCanaryYaml(
          serviceDefinitionType, strategyParameters, includeVerify);
    }
  }

  public String generateBasicYaml(String accountIdentifier, ServiceDefinitionType serviceDefinitionType,
      boolean includeVerify, StrategyParameters strategyParameters) throws IOException {
    try {
      return generateSshWinRmBasicYaml(accountIdentifier, serviceDefinitionType, strategyParameters, includeVerify);
    } catch (Exception e) {
      log.error("Error occurred while generating execution yaml from template", e);
      return cdngPipelineExecutionStrategyHelper.generateSshWinRmBasicYaml(
          serviceDefinitionType, strategyParameters, includeVerify);
    }
  }

  public String generateRollingYaml(String accountIdentifier, ServiceDefinitionType serviceDefinitionType,
      boolean includeVerify, StrategyParameters strategyParameters) throws IOException {
    try {
      return generateSshWinRmRollingYaml(accountIdentifier, serviceDefinitionType, strategyParameters, includeVerify);
    } catch (Exception e) {
      log.error("Error occurred while generating execution yaml from template", e);
      return cdngPipelineExecutionStrategyHelper.generateSshWinRmRollingYaml(
          serviceDefinitionType, strategyParameters, includeVerify);
    }
  }

  @VisibleForTesting
  protected String generateSshWinRmBasicYaml(String accountIdentifier, ServiceDefinitionType serviceDefinitionType,
      StrategyParameters strategyParameters, boolean includeVerify) throws IOException {
    StrategyValidator.validateStrategyParametersForBasic(strategyParameters);
    createAccountLevelTemplateIfDoesntExist(
        accountIdentifier, serviceDefinitionType, strategyParameters.getArtifactType());
    try (StringWriter stringWriter = new StringWriter()) {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      String artifactType = ExecutionStrategyUtils.resolveArtifactTypeSuffix(serviceDefinitionType, strategyParameters);
      String basicSnippet = Resources.toString(Objects.requireNonNull(classLoader.getResource(
                                                   format("snippets/Pipelines/execution/v2/basic/%s/%s-basic-%s%s.yaml",
                                                       serviceDefinitionType.name().toLowerCase(Locale.ROOT),
                                                       serviceDefinitionType.name().toLowerCase(Locale.ROOT),
                                                       artifactType, includeVerify ? "-with-verify" : ""))),
          StandardCharsets.UTF_8);
      Map<String, Object> templateParams = ImmutableMap.<String, Object>builder()
                                               .put("failureStrategies", failureStrategiesSnippet)
                                               .put("basicSnippet", basicSnippet)
                                               .build();
      try {
        ExecutionStrategyTemplates.getTemplate(SSH_WINRM_BASIC_SH_FTL_V2).process(templateParams, stringWriter);
      } catch (TemplateException te) {
        throw new InvalidArgumentsException("Error processing yaml template: " + te.getMessage());
      }
      return stringWriter.toString().trim();
    }
  }

  @VisibleForTesting
  protected String generateSshWinRmRollingYaml(String accountIdentifier, ServiceDefinitionType serviceDefinitionType,
      StrategyParameters strategyParameters, boolean includeVerify) throws IOException {
    StrategyValidator.validateStrategyParametersForRolling(strategyParameters);
    createAccountLevelTemplateIfDoesntExist(
        accountIdentifier, serviceDefinitionType, strategyParameters.getArtifactType());
    try (StringWriter stringWriter = new StringWriter()) {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      String artifactType = ExecutionStrategyUtils.resolveArtifactTypeSuffix(serviceDefinitionType, strategyParameters);
      String rollingSnippet = Resources.toString(Objects.requireNonNull(classLoader.getResource(format(
                                                     "snippets/Pipelines/execution/v2/rolling/%s/%s-rolling-%s%s.yaml",
                                                     serviceDefinitionType.name().toLowerCase(Locale.ROOT),
                                                     serviceDefinitionType.name().toLowerCase(Locale.ROOT),
                                                     artifactType, includeVerify ? "-with-verify" : ""))),
          StandardCharsets.UTF_8);
      Map<String, Object> templateParams =
          ImmutableMap.<String, Object>builder()
              .put("failureStrategies", failureStrategiesSnippet)
              .put("maxConcurrency", 1)
              .put("partitionSize", strategyParameters.getInstances())
              .put("rollingSnippet", rollingSnippet)
              .put("unitType",
                  NGInstanceUnitType.PERCENTAGE.equals(strategyParameters.getUnitType()) ? "Percentage" : "Count")
              .build();
      try {
        ExecutionStrategyTemplates.getTemplate(SSH_WINRM_ROLLING_SH_FTL_V2).process(templateParams, stringWriter);
      } catch (TemplateException te) {
        throw new InvalidArgumentsException("Error processing yaml template: " + te.getMessage());
      }
      return stringWriter.toString().trim();
    }
  }

  @VisibleForTesting
  protected String generateSshWinRmCanaryYaml(String accountIdentifier, ServiceDefinitionType serviceDefinitionType,
      StrategyParameters strategyParameters, boolean includeVerify) throws IOException {
    StrategyValidator.validateStrategyParametersForCanary(strategyParameters);
    createAccountLevelTemplateIfDoesntExist(
        accountIdentifier, serviceDefinitionType, strategyParameters.getArtifactType());
    try (StringWriter stringWriter = new StringWriter()) {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      String artifactType = ExecutionStrategyUtils.resolveArtifactTypeSuffix(serviceDefinitionType, strategyParameters);
      String canarySnippet = Resources.toString(Objects.requireNonNull(classLoader.getResource(format(
                                                    "snippets/Pipelines/execution/v2/canary/%s/%s-canary-%s%s.yaml",
                                                    serviceDefinitionType.name().toLowerCase(Locale.ROOT),
                                                    serviceDefinitionType.name().toLowerCase(Locale.ROOT), artifactType,
                                                    includeVerify ? "-with-verify" : ""))),
          StandardCharsets.UTF_8);
      String canaryRollbackSnippet = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource(format("snippets/Pipelines/execution/v2/canary/%s/%s-canary-%s%s-rollback.yaml",
                  serviceDefinitionType.name().toLowerCase(Locale.ROOT),
                  serviceDefinitionType.name().toLowerCase(Locale.ROOT), artifactType,
                  includeVerify ? "-with-verify" : ""))),
          StandardCharsets.UTF_8);
      Map<String, Object> templateParams =
          ImmutableMap.<String, Object>builder()
              .put("failureStrategies", failureStrategiesSnippet)
              .put("phases", strategyParameters.getPhases())
              .put("unitType",
                  NGInstanceUnitType.PERCENTAGE.equals(strategyParameters.getUnitType()) ? "Percentage" : "Count")
              .put("canarySnippet", canarySnippet)
              .put("canaryRollbackSnippet", canaryRollbackSnippet)
              .build();
      try {
        ExecutionStrategyTemplates.getTemplate(SSH_WINRM_CANARY_SH_FTL_V2).process(templateParams, stringWriter);
      } catch (TemplateException te) {
        throw new InvalidArgumentsException("Error processing yaml template: " + te.getMessage());
      }
      return stringWriter.toString().trim();
    }
  }

  private void createAccountLevelTemplateIfDoesntExist(
      String accountId, ServiceDefinitionType serviceDefinitionType, ArtifactType artifactType) {
    DefaultTempateLoader.TemplateConfig templateConfig =
        DefaultTempateLoader.resolveTemplateConfig(serviceDefinitionType, artifactType);
    createAccountLevelTemplateIfDoesntExist(accountId, templateConfig);
  }

  private void createAccountLevelTemplateIfDoesntExist(
      String accountId, DefaultTempateLoader.TemplateConfig templateConfig) {
    try {
      TemplateResponseDTO response = NGRestUtils.getResponse(
          templateResourceClient.get(templateConfig.getTemplateIdentifier(), accountId, null, null, null, false));
      if (response == null) {
        throw new UnexpectedException(format(
            "Failed to fetch default template % from template service.", templateConfig.getTemplateIdentifier()));
      }
    } catch (InvalidRequestException e) {
      if (e.getMessage().contains("does not exist or has been deleted")) {
        log.info(format("Creating default template with identifier %s", templateConfig.getTemplateIdentifier()));
        YamlFullRefreshResponseDTO templateResponse = NGRestUtils.getResponse(templateResourceClient.create(accountId,
            null, null, RequestBody.create(MediaType.parse("application/json"), templateConfig.getTemplateYaml()), true,
            templateConfig.getTemplateComment()));
        String templateYaml =
            templateResponse != null ? getRefreshedYamlOrElseYamlFromConfig(templateConfig, templateResponse) : "";
        if (isEmpty(templateYaml)) {
          throw new InvalidRequestException(
              format("Default template % initialized with empty yaml content", templateConfig.getTemplateIdentifier()));
        }
      } else {
        log.error("Error occurred while creating default template", e);
        throw e;
      }
    }
  }

  private String getRefreshedYamlOrElseYamlFromConfig(
      DefaultTempateLoader.TemplateConfig templateConfig, YamlFullRefreshResponseDTO templateResponse) {
    return isEmpty(templateResponse.getRefreshedYaml()) ? templateConfig.getTemplateYaml()
                                                        : templateResponse.getRefreshedYaml();
  }
}
