/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.beans.FeatureName.CI_CACHE_INTELLIGENCE;
import static io.harness.beans.steps.CIStepInfoType.CIStepExecEnvironment;
import static io.harness.beans.steps.CIStepInfoType.CIStepExecEnvironment.CI_MANAGER;
import static io.harness.beans.steps.CIStepInfoType.RESTORE_CACHE_GCS;
import static io.harness.beans.steps.CIStepInfoType.SAVE_CACHE_GCS;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_AUTO_CACHE_ACCOUNT_ID;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_AUTO_DETECT_CACHE;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_BACKEND_OPERATION_TIMEOUT;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_CACHE_KEY;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_FAIL_RESTORE_IF_KEY_NOT_PRESENT;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_MOUNT;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_OVERRIDE;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_REBUILD;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_RESTORE;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_DEPTH_ATTRIBUTE;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_NAME;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_SSL_NO_VERIFY;
import static io.harness.ci.commonconstants.CIExecutionConstants.PR_CLONE_STRATEGY_ATTRIBUTE;
import static io.harness.ci.commonconstants.CIExecutionConstants.RESTORE_CACHE_STEP_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.RESTORE_CACHE_STEP_NAME;
import static io.harness.ci.commonconstants.CIExecutionConstants.SAVE_CACHE_STEP_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.SAVE_CACHE_STEP_NAME;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.InitializeStepNode;
import io.harness.beans.steps.nodes.PluginStepNode;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.yaml.extended.cache.Caching;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.failurestrategy.NGFailureType;
import io.harness.yaml.core.failurestrategy.OnFailureConfig;
import io.harness.yaml.core.failurestrategy.ignore.IgnoreFailureActionConfig;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIStepGroupUtils {
  private static final String INITIALIZE_TASK = InitializeStepInfo.STEP_TYPE.getType();
  @Inject private InitializeStepGenerator initializeStepGenerator;
  @Inject private CIExecutionConfigService ciExecutionConfigService;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private VmInitializeTaskParamsBuilder vmInitializeTaskParamsBuilder;
  @Inject private CIFeatureFlagService featureFlagService;

  private static final String STRING_TRUE = "true";
  private static final String STRING_FALSE = "false";
  private static final String TEN_K_SECONDS = "10000s";
  private static final String ONE_HOUR = "1h";
  private static final String IMPLICIT_CACHE_STEP = "implicit_restore_cache";

  public List<ExecutionWrapperConfig> createExecutionWrapperWithInitializeStep(IntegrationStageNode stageNode,
      CIExecutionArgs ciExecutionArgs, CodeBase ciCodebase, Infrastructure infrastructure, String accountId) {
    List<ExecutionWrapperConfig> mainEngineExecutionSections = new ArrayList<>();

    IntegrationStageConfig integrationStageConfig = IntegrationStageUtils.getIntegrationStageConfig(stageNode);

    if (integrationStageConfig.getExecution() == null || isEmpty(integrationStageConfig.getExecution().getSteps())) {
      return mainEngineExecutionSections;
    }

    List<ExecutionWrapperConfig> executionSections = integrationStageConfig.getExecution().getSteps();

    log.info("Creating CI execution wrapper step info with initialize step for integration stage {} ",
        stageNode.getIdentifier());

    List<ExecutionWrapperConfig> initializeExecutionSections = new ArrayList<>();
    boolean gitClone = RunTimeInputHandler.resolveGitClone(integrationStageConfig.getCloneCodebase());
    Caching caching = integrationStageConfig.getCaching();
    boolean saveCache = caching != null && RunTimeInputHandler.resolveBooleanParameter(caching.getEnabled(), false);
    boolean featureCacheEnabled = featureFlagService.isEnabled(CI_CACHE_INTELLIGENCE, accountId);
    boolean isHosted = infrastructure.getType().equals(Infrastructure.Type.HOSTED_VM)
        || infrastructure.getType().equals(Infrastructure.Type.KUBERNETES_HOSTED);
    if (gitClone) {
      initializeExecutionSections.add(
          getGitCloneStep(ciExecutionArgs, ciCodebase, accountId, IntegrationStageUtils.getK8OS(infrastructure)));
    }
    boolean enableCacheIntel = featureCacheEnabled && saveCache && isHosted;
    if (enableCacheIntel) {
      initializeExecutionSections.add(getRestoreCacheStep(caching, accountId));
    }
    initializeExecutionSections.addAll(executionSections);

    if (enableCacheIntel) {
      initializeExecutionSections.add(getSaveCacheStep(caching, accountId));
    }
    if (isNotEmpty(initializeExecutionSections)) {
      ExecutionWrapperConfig liteEngineStepExecutionWrapper = fetchInitializeStepExecutionWrapper(
          initializeExecutionSections, stageNode, ciExecutionArgs, ciCodebase, infrastructure, accountId);

      mainEngineExecutionSections.add(liteEngineStepExecutionWrapper);
      // Also execute each step individually on main engine
      mainEngineExecutionSections.addAll(initializeExecutionSections);
    }

    return mainEngineExecutionSections;
  }

  public static String getUniqueStepIdentifier(List<Level> levels, String stepIdentifier) {
    StringBuilder identifier = new StringBuilder();
    for (Level level : levels) {
      if (level.getStepType().getType().equals("STEP_GROUP")) {
        identifier.append(level.getIdentifier());
        identifier.append("_");
      }
    }
    identifier.append(stepIdentifier);
    return identifier.toString();
  }

  private ExecutionWrapperConfig fetchInitializeStepExecutionWrapper(
      List<ExecutionWrapperConfig> liteEngineExecutionSections, IntegrationStageNode integrationStage,
      CIExecutionArgs ciExecutionArgs, CodeBase ciCodebase, Infrastructure infrastructure, String accountId) {
    // TODO Do not generate new id
    InitializeStepInfo initializeStepInfo = initializeStepGenerator.createInitializeStepInfo(
        ExecutionElementConfig.builder().uuid(generateUuid()).steps(liteEngineExecutionSections).build(), ciCodebase,
        integrationStage, ciExecutionArgs, infrastructure, accountId);

    try {
      String uuid = generateUuid();
      String jsonString = JsonPipelineUtils.writeJsonString(InitializeStepNode.builder()
                                                                .identifier(INITIALIZE_TASK)
                                                                .name(INITIALIZE_TASK)
                                                                .uuid(generateUuid())
                                                                .type(InitializeStepNode.StepType.liteEngineTask)
                                                                .timeout(getTimeout(infrastructure))
                                                                .initializeStepInfo(initializeStepInfo)
                                                                .build());
      JsonNode jsonNode = JsonPipelineUtils.getMapper().readTree(jsonString);
      return ExecutionWrapperConfig.builder().uuid(uuid).step(jsonNode).build();
    } catch (IOException e) {
      throw new CIStageExecutionException("Failed to create gitclone step", e);
    }
  }

  private boolean isLiteEngineStep(ExecutionWrapperConfig executionWrapper) {
    return !isCIManagerStep(executionWrapper);
  }

  private ParameterField<Timeout> getTimeout(Infrastructure infrastructure) {
    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    if (infrastructure.getType() == Infrastructure.Type.VM) {
      vmInitializeTaskParamsBuilder.validateInfrastructure(infrastructure);
      VmPoolYaml vmPoolYaml = (VmPoolYaml) ((VmInfraYaml) infrastructure).getSpec();
      return parseTimeout(vmPoolYaml.getSpec().getInitTimeout(), "15m");
    } else if (infrastructure.getType() == Infrastructure.Type.KUBERNETES_DIRECT) {
      if (((K8sDirectInfraYaml) infrastructure).getSpec() == null) {
        throw new CIStageExecutionException("Input infrastructure can not be empty");
      }
      ParameterField<String> timeout = ((K8sDirectInfraYaml) infrastructure).getSpec().getInitTimeout();
      return parseTimeout(timeout, "10m");
    } else {
      return ParameterField.createValueField(Timeout.fromString("10m"));
    }
  }

  private ParameterField<Timeout> parseTimeout(ParameterField<String> timeout, String defaultTimeout) {
    if (timeout != null && timeout.fetchFinalValue() != null && isNotEmpty((String) timeout.fetchFinalValue())) {
      return ParameterField.createValueField(Timeout.fromString((String) timeout.fetchFinalValue()));
    } else {
      return ParameterField.createValueField(Timeout.fromString(defaultTimeout));
    }
  }

  private boolean isCIManagerStep(ExecutionWrapperConfig executionWrapperConfig) {
    if (executionWrapperConfig != null) {
      if (executionWrapperConfig.getStep() != null && !executionWrapperConfig.getStep().isNull()) {
        CIAbstractStepNode stepNode = IntegrationStageUtils.getStepNode(executionWrapperConfig);
        if (stepNode.getStepSpecType() instanceof CIStepInfo) {
          CIStepInfo ciStepInfo = (CIStepInfo) stepNode.getStepSpecType();
          return ciStepInfo.getNonYamlInfo().getStepInfoType().getCiStepExecEnvironment() == CI_MANAGER;
        } else {
          throw new InvalidRequestException("Non CIStepInfo is not supported");
        }
      } else if (executionWrapperConfig.getParallel() != null && !executionWrapperConfig.getParallel().isNull()) {
        ParallelStepElementConfig parallelStepElementConfig =
            IntegrationStageUtils.getParallelStepElementConfig(executionWrapperConfig);

        CIStepExecEnvironment ciStepExecEnvironment = validateAndFetchParallelStepsType(parallelStepElementConfig);
        return ciStepExecEnvironment == CI_MANAGER;
      } else {
        throw new InvalidRequestException("Only Parallel or StepElement is supported");
      }
    }
    return false;
  }

  private CIStepExecEnvironment validateAndFetchParallelStepsType(ParallelStepElementConfig parallel) {
    CIStepExecEnvironment ciStepExecEnvironment = null;

    if (isNotEmpty(parallel.getSections())) {
      for (ExecutionWrapperConfig executionWrapper : parallel.getSections()) {
        if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
          CIAbstractStepNode stepNode = IntegrationStageUtils.getStepNode(executionWrapper);

          if (stepNode.getStepSpecType() instanceof CIStepInfo) {
            CIStepInfo ciStepInfo = (CIStepInfo) stepNode.getStepSpecType();
            if (ciStepExecEnvironment == null
                || (ciStepExecEnvironment
                    == ciStepInfo.getNonYamlInfo().getStepInfoType().getCiStepExecEnvironment())) {
              ciStepExecEnvironment = ciStepInfo.getNonYamlInfo().getStepInfoType().getCiStepExecEnvironment();
            } else {
              throw new InvalidRequestException("All parallel steps can either run on manager or on lite engine");
            }
          } else {
            throw new InvalidRequestException("Non CIStepInfo is not supported");
          }
        }
      }
    }
    return ciStepExecEnvironment;
  }

  private ExecutionWrapperConfig getGitCloneStep(
      CIExecutionArgs ciExecutionArgs, CodeBase ciCodebase, String accountId, OSType os) {
    Map<String, JsonNode> settings = new HashMap<>();
    if (ciCodebase == null) {
      throw new CIStageExecutionException("Codebase is mandatory with enabled cloneCodebase flag");
    }
    Integer depth = ciCodebase.getDepth().getValue();
    ExecutionSource executionSource = ciExecutionArgs.getExecutionSource();
    if (depth == null) {
      if (executionSource != null && executionSource.getType() == ExecutionSource.Type.MANUAL) {
        ManualExecutionSource manualExecutionSource = (ManualExecutionSource) executionSource;
        if (isNotEmpty(manualExecutionSource.getBranch()) || isNotEmpty(manualExecutionSource.getTag())) {
          depth = GIT_CLONE_MANUAL_DEPTH;
        }
      }
    }

    if (depth != null) {
      settings.put(GIT_CLONE_DEPTH_ATTRIBUTE, JsonNodeFactory.instance.textNode(depth.toString()));
    }

    if (ciCodebase.getPrCloneStrategy().getValue() != null) {
      settings.put(PR_CLONE_STRATEGY_ATTRIBUTE,
          JsonNodeFactory.instance.textNode(ciCodebase.getPrCloneStrategy().getValue().getYamlName()));
    }

    Map<String, ParameterField<String>> envVariables = new HashMap<>();
    if (ciCodebase.getSslVerify().getValue() != null && !ciCodebase.getSslVerify().getValue()) {
      envVariables.put(GIT_SSL_NO_VERIFY, ParameterField.createValueField(STRING_TRUE));
    }

    List<String> entrypoint = ciExecutionServiceConfig.getStepConfig().getGitCloneConfig().getEntrypoint();
    if (os == OSType.Windows) {
      entrypoint = ciExecutionServiceConfig.getStepConfig().getGitCloneConfig().getWindowsEntrypoint();
    }

    String gitCloneImage =
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GIT_CLONE, accountId).getImage();
    PluginStepInfo step = PluginStepInfo.builder()
                              .identifier(GIT_CLONE_STEP_ID)
                              .image(ParameterField.createValueField(gitCloneImage))
                              .name(GIT_CLONE_STEP_NAME)
                              .settings(ParameterField.createValueField(settings))
                              .envVariables(ParameterField.createValueField(envVariables))
                              .entrypoint(ParameterField.createValueField(entrypoint))
                              .harnessManagedImage(true)
                              .resources(ciCodebase.getResources())
                              .build();

    String uuid = generateUuid();
    PluginStepNode pluginStepNode =
        PluginStepNode.builder()
            .identifier(GIT_CLONE_STEP_ID)
            .name(GIT_CLONE_STEP_NAME)
            .timeout(ParameterField.createValueField(Timeout.builder().timeoutString(ONE_HOUR).build()))
            .uuid(generateUuid())
            .type(PluginStepNode.StepType.Plugin)
            .pluginStepInfo(step)
            .build();

    try {
      String jsonString = JsonPipelineUtils.writeJsonString(pluginStepNode);
      JsonNode jsonNode = JsonPipelineUtils.getMapper().readTree(jsonString);
      return ExecutionWrapperConfig.builder().uuid(uuid).step(jsonNode).build();
    } catch (IOException e) {
      throw new CIStageExecutionException("Failed to create gitclone step", e);
    }
  }

  private ExecutionWrapperConfig getRestoreCacheStep(Caching caching, String accountId) {
    Map<String, JsonNode> settings = new HashMap<>();
    Map<String, ParameterField<String>> envVariables = new HashMap<>();
    String uuid = generateUuid();
    String restoreCacheImage = ciExecutionConfigService.getPluginVersionForK8(RESTORE_CACHE_GCS, accountId).getImage();
    List<String> entrypoint = ciExecutionServiceConfig.getStepConfig().getCacheGCSConfig().getEntrypoint();

    setCacheEnvVariables(envVariables, caching, accountId);
    envVariables.put(PLUGIN_RESTORE, ParameterField.createValueField(STRING_TRUE));

    envVariables.put(PLUGIN_FAIL_RESTORE_IF_KEY_NOT_PRESENT, ParameterField.createValueField(STRING_FALSE));
    envVariables.put(PLUGIN_BACKEND_OPERATION_TIMEOUT, ParameterField.createValueField(TEN_K_SECONDS));

    PluginStepInfo step = PluginStepInfo.builder()
                              .identifier(RESTORE_CACHE_STEP_ID)
                              .image(ParameterField.createValueField(restoreCacheImage))
                              .name(RESTORE_CACHE_STEP_NAME)
                              .settings(ParameterField.createValueField(settings))
                              .envVariables(ParameterField.createValueField(envVariables))
                              .entrypoint(ParameterField.createValueField(entrypoint))
                              .harnessManagedImage(true)
                              .build();

    PluginStepNode pluginStepNode =
        PluginStepNode.builder()
            .identifier(RESTORE_CACHE_STEP_ID)
            .name(RESTORE_CACHE_STEP_NAME)
            .timeout(ParameterField.createValueField(Timeout.builder().timeoutString(ONE_HOUR).build()))
            .uuid(generateUuid())
            .type(PluginStepNode.StepType.Plugin)
            .pluginStepInfo(step)
            .build();
    try {
      String jsonString = JsonPipelineUtils.writeJsonString(pluginStepNode);
      JsonNode jsonNode = JsonPipelineUtils.getMapper().readTree(jsonString);
      return ExecutionWrapperConfig.builder().uuid(uuid).step(jsonNode).build();
    } catch (IOException e) {
      throw new CIStageExecutionException("Failed to create restore cache step", e);
    }
  }

  private ExecutionWrapperConfig getSaveCacheStep(Caching caching, String accountId) {
    Map<String, JsonNode> settings = new HashMap<>();
    Map<String, ParameterField<String>> envVariables = new HashMap<>();
    String uuid = generateUuid();
    String saveCacheImage = ciExecutionConfigService.getPluginVersionForK8(SAVE_CACHE_GCS, accountId).getImage();
    List<String> entrypoint = ciExecutionServiceConfig.getStepConfig().getCacheGCSConfig().getEntrypoint();

    setCacheEnvVariables(envVariables, caching, accountId);
    // We will override cache for cache intel for now. Might need to surface it as an option
    envVariables.put(PLUGIN_OVERRIDE, ParameterField.createValueField(STRING_TRUE));
    envVariables.put(PLUGIN_REBUILD, ParameterField.createValueField(STRING_TRUE));

    PluginStepInfo step = PluginStepInfo.builder()
                              .identifier(SAVE_CACHE_STEP_ID)
                              .image(ParameterField.createValueField(saveCacheImage))
                              .name(SAVE_CACHE_STEP_NAME)
                              .settings(ParameterField.createValueField(settings))
                              .envVariables(ParameterField.createValueField(envVariables))
                              .entrypoint(ParameterField.createValueField(entrypoint))
                              .harnessManagedImage(true)
                              .build();

    OnFailureConfig onFailureConfig = OnFailureConfig.builder()
                                          .errors(Collections.singletonList(NGFailureType.ALL_ERRORS))
                                          .action(IgnoreFailureActionConfig.builder().build())
                                          .build();
    FailureStrategyConfig failureStrategyConfig = FailureStrategyConfig.builder().onFailure(onFailureConfig).build();
    PluginStepNode pluginStepNode =
        PluginStepNode.builder()
            .identifier(SAVE_CACHE_STEP_ID)
            .name(SAVE_CACHE_STEP_NAME)
            .timeout(ParameterField.createValueField(Timeout.builder().timeoutString(ONE_HOUR).build()))
            .uuid(generateUuid())
            .type(PluginStepNode.StepType.Plugin)
            .pluginStepInfo(step)
            .failureStrategies(ParameterField.createValueField(Collections.singletonList(failureStrategyConfig)))
            .build();
    try {
      String jsonString = JsonPipelineUtils.writeJsonString(pluginStepNode);
      JsonNode jsonNode = JsonPipelineUtils.getMapper().readTree(jsonString);
      return ExecutionWrapperConfig.builder().uuid(uuid).step(jsonNode).build();
    } catch (IOException e) {
      throw new CIStageExecutionException("Failed to create restore cache step", e);
    }
  }

  private void setCacheEnvVariables(
      Map<String, ParameterField<String>> envVariables, Caching caching, String accountId) {
    List<String> cacheDir = new ArrayList<>();
    if (caching != null) {
      if (caching.getPaths() != null) {
        cacheDir = RunTimeInputHandler.resolveListParameter(
            "paths", IMPLICIT_CACHE_STEP, IMPLICIT_CACHE_STEP, caching.getPaths(), false);
      }
      if (caching.getKey() != null) {
        String cacheKey = RunTimeInputHandler.resolveStringParameterV2(
            "key", IMPLICIT_CACHE_STEP, IMPLICIT_CACHE_STEP, caching.getKey(), false);
        envVariables.put(PLUGIN_CACHE_KEY, ParameterField.createValueField(cacheKey));
      }
    }
    envVariables.put(PLUGIN_AUTO_DETECT_CACHE, ParameterField.createValueField(STRING_TRUE));
    envVariables.put(PLUGIN_AUTO_CACHE_ACCOUNT_ID, ParameterField.createValueField(accountId));
    if (cacheDir != null && cacheDir.size() > 0) {
      envVariables.put(PLUGIN_MOUNT, ParameterField.createValueField(String.join(",", cacheDir)));
    }
    envVariables.put(PLUGIN_BACKEND_OPERATION_TIMEOUT, ParameterField.createValueField(TEN_K_SECONDS));
  }
}
