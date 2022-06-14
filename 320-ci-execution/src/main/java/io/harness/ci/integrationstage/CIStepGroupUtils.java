/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.beans.steps.CIStepInfoType.CIStepExecEnvironment;
import static io.harness.beans.steps.CIStepInfoType.CIStepExecEnvironment.CI_MANAGER;
import static io.harness.common.CIExecutionConstants.GIT_CLONE_DEPTH_ATTRIBUTE;
import static io.harness.common.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.common.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.common.CIExecutionConstants.GIT_CLONE_STEP_NAME;
import static io.harness.common.CIExecutionConstants.GIT_SSL_NO_VERIFY;
import static io.harness.common.CIExecutionConstants.PR_CLONE_STRATEGY_ATTRIBUTE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.IntegrationStageConfig;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.execution.CIExecutionConfigService;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
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

  public List<ExecutionWrapperConfig> createExecutionWrapperWithInitializeStep(StageElementConfig stageElementConfig,
      CIExecutionArgs ciExecutionArgs, CodeBase ciCodebase, Infrastructure infrastructure, String accountId) {
    List<ExecutionWrapperConfig> mainEngineExecutionSections = new ArrayList<>();

    IntegrationStageConfig integrationStageConfig = IntegrationStageUtils.getIntegrationStageConfig(stageElementConfig);

    if (integrationStageConfig.getExecution() == null || isEmpty(integrationStageConfig.getExecution().getSteps())) {
      return mainEngineExecutionSections;
    }

    List<ExecutionWrapperConfig> executionSections = integrationStageConfig.getExecution().getSteps();

    log.info("Creating CI execution wrapper step info with initialize step for integration stage {} ",
        stageElementConfig.getIdentifier());

    List<ExecutionWrapperConfig> initializeExecutionSections = new ArrayList<>();
    boolean gitClone = RunTimeInputHandler.resolveGitClone(integrationStageConfig.getCloneCodebase());

    if (gitClone) {
      initializeExecutionSections.add(
          getGitCloneStep(ciExecutionArgs, ciCodebase, accountId, IntegrationStageUtils.getK8OS(infrastructure)));
    }
    for (ExecutionWrapperConfig executionWrapper : executionSections) {
      initializeExecutionSections.add(executionWrapper);
    }

    if (isNotEmpty(initializeExecutionSections)) {
      ExecutionWrapperConfig liteEngineStepExecutionWrapper = fetchInitializeStepExecutionWrapper(
          initializeExecutionSections, stageElementConfig, ciExecutionArgs, ciCodebase, infrastructure, accountId);

      mainEngineExecutionSections.add(liteEngineStepExecutionWrapper);
      // Also execute each step individually on main engine
      mainEngineExecutionSections.addAll(initializeExecutionSections);
    }

    return mainEngineExecutionSections;
  }

  private ExecutionWrapperConfig fetchInitializeStepExecutionWrapper(
      List<ExecutionWrapperConfig> liteEngineExecutionSections, StageElementConfig integrationStage,
      CIExecutionArgs ciExecutionArgs, CodeBase ciCodebase, Infrastructure infrastructure, String accountId) {
    // TODO Do not generate new id
    InitializeStepInfo initializeStepInfo = initializeStepGenerator.createInitializeStepInfo(
        ExecutionElementConfig.builder().uuid(generateUuid()).steps(liteEngineExecutionSections).build(), ciCodebase,
        integrationStage, ciExecutionArgs, infrastructure, accountId);

    try {
      String uuid = generateUuid();
      String jsonString = JsonPipelineUtils.writeJsonString(StepElementConfig.builder()
                                                                .identifier(INITIALIZE_TASK)
                                                                .name(INITIALIZE_TASK)
                                                                .uuid(generateUuid())
                                                                .type(INITIALIZE_TASK)
                                                                .timeout(getTimeout(infrastructure))
                                                                .stepSpecType(initializeStepInfo)
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

    if (infrastructure.getType() != Infrastructure.Type.KUBERNETES_DIRECT) {
      return ParameterField.createValueField(Timeout.fromString("10m"));
    }

    if (((K8sDirectInfraYaml) infrastructure).getSpec() == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }
    ParameterField<String> timeout = ((K8sDirectInfraYaml) infrastructure).getSpec().getInitTimeout();

    if (timeout != null && timeout.fetchFinalValue() != null && isNotEmpty((String) timeout.fetchFinalValue())) {
      return ParameterField.createValueField(Timeout.fromString((String) timeout.fetchFinalValue()));
    } else {
      return ParameterField.createValueField(Timeout.fromString("10m"));
    }
  }

  private boolean isCIManagerStep(ExecutionWrapperConfig executionWrapperConfig) {
    if (executionWrapperConfig != null) {
      if (executionWrapperConfig.getStep() != null && !executionWrapperConfig.getStep().isNull()) {
        StepElementConfig stepElementConfig = IntegrationStageUtils.getStepElementConfig(executionWrapperConfig);
        if (stepElementConfig.getStepSpecType() instanceof CIStepInfo) {
          CIStepInfo ciStepInfo = (CIStepInfo) stepElementConfig.getStepSpecType();
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
          StepElementConfig stepElementConfig = IntegrationStageUtils.getStepElementConfig(executionWrapper);

          if (stepElementConfig.getStepSpecType() instanceof CIStepInfo) {
            CIStepInfo ciStepInfo = (CIStepInfo) stepElementConfig.getStepSpecType();
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
      if (executionSource.getType() == ExecutionSource.Type.MANUAL) {
        ManualExecutionSource manualExecutionSource = (ManualExecutionSource) executionSource;
        if (isNotEmpty(manualExecutionSource.getBranch()) || isNotEmpty(manualExecutionSource.getTag())) {
          depth = GIT_CLONE_MANUAL_DEPTH;
        }
      }
    }

    if (depth != null && depth != 0) {
      settings.put(GIT_CLONE_DEPTH_ATTRIBUTE, JsonNodeFactory.instance.textNode(depth.toString()));
    }

    if (ciCodebase.getPrCloneStrategy().getValue() != null) {
      settings.put(PR_CLONE_STRATEGY_ATTRIBUTE,
          JsonNodeFactory.instance.textNode(ciCodebase.getPrCloneStrategy().getValue().getYamlName()));
    }

    Map<String, String> envVariables = new HashMap<>();
    if (ciCodebase.getSslVerify().getValue() != null && !ciCodebase.getSslVerify().getValue()) {
      envVariables.put(GIT_SSL_NO_VERIFY, "true");
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
                              .envVariables(envVariables)
                              .entrypoint(entrypoint)
                              .harnessManagedImage(true)
                              .resources(ciCodebase.getResources())
                              .build();

    String uuid = generateUuid();
    StepElementConfig stepElementConfig =
        StepElementConfig.builder()
            .identifier(GIT_CLONE_STEP_ID)
            .name(GIT_CLONE_STEP_NAME)
            .timeout(ParameterField.createValueField(Timeout.builder().timeoutString("1h").build()))
            .uuid(generateUuid())
            .type("Plugin")
            .stepSpecType(step)
            .build();

    try {
      String jsonString = JsonPipelineUtils.writeJsonString(stepElementConfig);
      JsonNode jsonNode = JsonPipelineUtils.getMapper().readTree(jsonString);
      return ExecutionWrapperConfig.builder().uuid(uuid).step(jsonNode).build();
    } catch (IOException e) {
      throw new CIStageExecutionException("Failed to create gitclone step", e);
    }
  }
}
