/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.beans.serializer.RunTimeInputHandler.UNRESOLVED_PARAMETER;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveArchiveFormat;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveBooleanParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveListParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.beans.steps.CIStepInfoType.CIStepExecEnvironment;
import static io.harness.beans.steps.CIStepInfoType.CIStepExecEnvironment.CI_MANAGER;
import static io.harness.beans.steps.CIStepInfoType.RESTORE_CACHE_GCS;
import static io.harness.beans.steps.CIStepInfoType.SAVE_CACHE_GCS;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_ARCHIVE_FORMAT;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_ARTIFACT_FILE;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_BACKEND;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_BACKEND_OPERATION_TIMEOUT;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_BUCKET;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_CACHE_KEY;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_EXIT_CODE;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_FAIL_RESTORE_IF_KEY_NOT_PRESENT;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_MOUNT;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_OVERRIDE;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_REBUILD;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_RESTORE;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_SOURCE;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_TARGET;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_DEPTH_ATTRIBUTE;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_NAME;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_SSL_NO_VERIFY;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_ARTIFACT_FILE_VALUE;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_JSON_KEY;
import static io.harness.ci.commonconstants.CIExecutionConstants.PR_CLONE_STRATEGY_ATTRIBUTE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static java.lang.String.format;
import static org.springframework.util.StringUtils.trimLeadingCharacter;
import static org.springframework.util.StringUtils.trimTrailingCharacter;

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
import io.harness.beans.yaml.extended.ArchiveFormat;
import io.harness.beans.yaml.extended.cache.CacheOptions;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
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

  private String json = "{\n" +
          "  \"type\": \"service_account\",\n" +
          "  \"project_id\": \"ci-play\",\n" +
          "  \"private_key_id\": \"1324269daa496d42c404b97f96bdb0e7f57f7e9f\",\n" +
          "  \"private_key\": \"-----BEGIN PRIVATE KEY-----\\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCgM18WI4BWWaJz\\nMn2XiANp0VyfV1eEAgxCRA2sW5uHKQE8LgxtJacJcoHZRRZX+kM5Qf2Nd8w93NTx\\n360Hn7EqjyxqzRrlDcIvl14L3MGW+datI/xU6mcTspMZhMFZ+3MGTMu/7kGJHbKA\\nfHR9PIQJQXy/oCS5om1Pd1bPlSFUoqjKCC3yTSavYBy7X+SE6PTZEGyMhvMoQchZ\\n7mQqKhZUfb+5M8rP3xwul2wjgzeuQkxk3w8ZDczsdGwe05RqytHj5LGyGIQAKp4f\\nbxibYaaEAfzBl1I4JsdWqsAy03QCD6yulRTdRgI1hq4BxWdt/6xAemcTJgP3/TVR\\nm91WoYXZAgMBAAECggEAIacI93aXtAv6Qya9GULaLLVtNo+7c6CWgKkZEjbgMgFc\\nIA8wTxFUyHhEbKbFrc+FpZaGM9yRjAFCvliVWX+jUORomTrixnEgdKzgda92/0cW\\nYKKplBD1fD7MBdVMZKGcpRsmxfe/zpTtdW4vbktzFRqroPl8HX7QZZwVIWAbc1Ku\\nvvDF/pjWSQte0wL43kYm4o0PZ85QXviHs79i/7uz2LNBe4KM+ilsYAKa+63ySBbw\\n/pcUHl9TDWAqGg/Nd5P+c4BuU6uvY4Dk06aA+NTZ2Wrc73SJik083qk1T9To+zGn\\n52mDTfGDdnjg7RHECuNOI4MpQOm0WYB0Mz+rT1hcPQKBgQDaJJQOCqtRMOTimUTc\\nxdkVfFA5N8CT4fwRVtRtbRwMsxTIumPJV6LY5MSzqUI9dTbQzY4kSkPRPihblxq/\\nA5+seNpMxdV68zbMA2g6ZPmrQPGku0Ux5jwnaM7QLjGJZnoWIvfpxXXnIytCMt4e\\nNfGF7vlfz/tUWMybUDV/JB8m0wKBgQC8AJj7Q/EbgdW7eWnukFm6yoTre1i2iVQT\\nkuV5bFt1DCT++iA8JJF0vldJLhbXaXZsEqNWJkCmVaGX9GmlPAAii5WZ3Ix3dyAu\\nxx1zGRW46MofMR4+6X1y/Id/T9iNdmaT3cwDz73ILLM9Ip/vJnnyDPmkIUl+TbWZ\\nlVO5pe+NIwKBgHgpFfT2I5BBopK/YpNJ2F5hb79U2pubK8JRVgpAw+aq7pPzN+w8\\nfdODkGZ2oapA2sUBtX5/+gNUfd6VyYHWeSoEGBuaDhH/zvtqFQu1e2G+EF1xWpg1\\n/oSm0uURzO+mpzFyaaU3w85iLP32DywNAtGH5Y3FfufjUjFraUxzlUVJAoGAJwK8\\nu87CViHf1tH/0Df11pO1dyOWKfJfFtyxbzuz0prdhmcijzhLUn04oX/Fz5cbsps/\\nd13ipmE4cc6OqXHE2WY7ebzRDO0UKYC5tKts3Xy4jDZl/0n19Qk/mIRZ/CioamBo\\nuBXAXKwh2Tq5EyfZwAc/OfaKSIIZf7ADuo8bIR8CgYEAkWP9wO5jjQipKGr2zkSL\\nzhTggycdsgfffkWJnXMmjRHqdoWBoRfwsDw61OMwS9tM+fC6pI84WYfPg6oJtusM\\n5zchycAW6QN/e4KXAaP7Ba4uSMEyiEpRDEaObRmUKGvU5u1kkKovBcvE+/o1LwX2\\nucerw5Vv7TH1PakTLa/OKoc=\\n-----END PRIVATE KEY-----\\n\",\n" +
          "  \"client_email\": \"jamie-service-account@ci-play.iam.gserviceaccount.com\",\n" +
          "  \"client_id\": \"110958341991767740997\",\n" +
          "  \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n" +
          "  \"token_uri\": \"https://oauth2.googleapis.com/token\",\n" +
          "  \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\",\n" +
          "  \"client_x509_cert_url\": \"https://www.googleapis.com/robot/v1/metadata/x509/jamie-service-account%40ci-play.iam.gserviceaccount.com\"\n" +
          "}\n";

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
    CacheOptions cacheOptions = integrationStageConfig.getCacheOptions();
    boolean saveCache = cacheOptions != null && RunTimeInputHandler.resolveBooleanParameter(cacheOptions.getEnabled(), false);

    if (gitClone) {
      initializeExecutionSections.add(
          getGitCloneStep(ciExecutionArgs, ciCodebase, accountId, IntegrationStageUtils.getK8OS(infrastructure)));
    }
    if (saveCache) {
      initializeExecutionSections.add(
              getRestoreCacheStep(ciExecutionArgs, cacheOptions, accountId, IntegrationStageUtils.getK8OS(infrastructure)));
    }
    initializeExecutionSections.addAll(executionSections);

    if (saveCache) {
      initializeExecutionSections.add(
              getSaveCacheStep(ciExecutionArgs, cacheOptions, accountId, IntegrationStageUtils.getK8OS(infrastructure)));
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
                              .entrypoint(ParameterField.createValueField(entrypoint))
                              .harnessManagedImage(true)
                              .resources(ciCodebase.getResources())
                              .build();

    String uuid = generateUuid();
    PluginStepNode pluginStepNode =
        PluginStepNode.builder()
            .identifier(GIT_CLONE_STEP_ID)
            .name(GIT_CLONE_STEP_NAME)
            .timeout(ParameterField.createValueField(Timeout.builder().timeoutString("1h").build()))
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

  private ExecutionWrapperConfig getRestoreCacheStep(
          CIExecutionArgs ciExecutionArgs, CacheOptions cacheOptions, String accountId, OSType os) {
    Map<String, JsonNode> settings = new HashMap<>();
    Map<String, String> envVariables = new HashMap<>();
    String uuid = generateUuid();
    String restoreCacheImage =
            ciExecutionConfigService.getPluginVersionForK8(RESTORE_CACHE_GCS, accountId).getImage();
    List<String> entrypoint = ciExecutionServiceConfig.getStepConfig().getCacheGCSConfig().getEntrypoint();
    List<String> cacheDir = new ArrayList<>();
    if (cacheOptions.getCachedPaths() != null) {
      cacheDir = RunTimeInputHandler.resolveListParameter("cachedPaths", "implicit restore cache", "internal restore cache", cacheOptions.getCachedPaths(), false);
    }
    String cacheKey = accountId + "/" + "306b05461691e952e2b943de15ab13c486bcff7a2d3be5d4ed6f1714ac2f04f0";
    if (cacheDir != null) {
      cacheKey = String.join("/", accountId, String.valueOf(cacheDir.hashCode()));
    }

    envVariables.put(PLUGIN_CACHE_KEY, cacheKey);
    envVariables.put(PLUGIN_BUCKET, "harness_cache_local_test");

    envVariables.put(PLUGIN_RESTORE, "true");
    envVariables.put(PLUGIN_EXIT_CODE, "true");

    envVariables.put(PLUGIN_ARCHIVE_FORMAT, "tar");

    envVariables.put(PLUGIN_FAIL_RESTORE_IF_KEY_NOT_PRESENT, "false");
    envVariables.put(PLUGIN_BACKEND, "gcs");
    envVariables.put(PLUGIN_BACKEND_OPERATION_TIMEOUT, format("%ss", 10000));
    envVariables.put(PLUGIN_JSON_KEY, json);

    PluginStepInfo step = PluginStepInfo.builder()
            .identifier("restore-cache-harness")
            .image(ParameterField.createValueField(restoreCacheImage))
            .name("Restore Cache Harness")
            .settings(ParameterField.createValueField(settings))
            .envVariables(envVariables)
            .entrypoint(ParameterField.createValueField(entrypoint))
            .harnessManagedImage(true)
            .build();

    PluginStepNode pluginStepNode =
            PluginStepNode.builder()
                    .identifier("restore-cache-harness")
                    .name("Restore Cache Harness")
                    .timeout(ParameterField.createValueField(Timeout.builder().timeoutString("1h").build()))
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

  private ExecutionWrapperConfig getSaveCacheStep(
          CIExecutionArgs ciExecutionArgs, CacheOptions cacheOptions, String accountId, OSType os) {
    Map<String, JsonNode> settings = new HashMap<>();
    Map<String, String> envVariables = new HashMap<>();
    String uuid = generateUuid();
    String saveCacheImage =
            ciExecutionConfigService.getPluginVersionForK8(SAVE_CACHE_GCS, accountId).getImage();
    List<String> entrypoint = ciExecutionServiceConfig.getStepConfig().getCacheGCSConfig().getEntrypoint();

    List<String> cacheDir = new ArrayList<>();
    if (cacheOptions.getCachedPaths() != null) {
      cacheDir = RunTimeInputHandler.resolveListParameter("cachedPaths", "implicit restore cache", "internal restore cache", cacheOptions.getCachedPaths(), false);
    }
    String cacheKey = accountId + "/" + "306b05461691e952e2b943de15ab13c486bcff7a2d3be5d4ed6f1714ac2f04f0";
    if (cacheDir != null) {
      cacheKey = String.join("/", accountId, String.valueOf(cacheDir.hashCode()));
    } else {
      cacheDir = Collections.singletonList("/root/.mvn");
    }

    envVariables.put(PLUGIN_CACHE_KEY, cacheKey);
    envVariables.put(PLUGIN_BUCKET, "harness_cache_local_test");
    envVariables.put(PLUGIN_EXIT_CODE, "true");

    envVariables.put(PLUGIN_ARCHIVE_FORMAT, "tar");
    envVariables.put(PLUGIN_BACKEND, "gcs");
    envVariables.put(PLUGIN_BACKEND_OPERATION_TIMEOUT, format("%ss", 10000));

    envVariables.put(PLUGIN_OVERRIDE, String.valueOf(false));
    envVariables.put(PLUGIN_MOUNT, listToStringSlice(cacheDir));
    envVariables.put(PLUGIN_REBUILD, "true");

    envVariables.put(PLUGIN_JSON_KEY, json);

    PluginStepInfo step = PluginStepInfo.builder()
            .identifier("save-cache-harness")
            .image(ParameterField.createValueField(saveCacheImage))
            .name("Save Cache Harness")
            .settings(ParameterField.createValueField(settings))
            .envVariables(envVariables)
            .entrypoint(ParameterField.createValueField(entrypoint))
            .harnessManagedImage(true)
            .build();

    PluginStepNode pluginStepNode =
            PluginStepNode.builder()
                    .identifier("save-cache-harness")
                    .name("Save Cache Harness")
                    .timeout(ParameterField.createValueField(Timeout.builder().timeoutString("1h").build()))
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

  private static String listToStringSlice(List<String> stringList) {
    if (isEmpty(stringList)) {
      return "";
    }
    StringBuilder listAsString = new StringBuilder();
    for (String value : stringList) {
      listAsString.append(value).append(',');
    }
    listAsString.deleteCharAt(listAsString.length() - 1);
    return listAsString.toString();
  }

}
