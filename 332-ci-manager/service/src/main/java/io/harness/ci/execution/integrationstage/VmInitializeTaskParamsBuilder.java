/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameter;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.data.encoding.EncodingUtils.decodeBase64;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.vm.CIVMConstants.DRONE_COMMIT_BRANCH;
import static io.harness.vm.CIVMConstants.DRONE_COMMIT_LINK;
import static io.harness.vm.CIVMConstants.DRONE_COMMIT_SHA;
import static io.harness.vm.CIVMConstants.DRONE_REMOTE_URL;
import static io.harness.vm.CIVMConstants.DRONE_SOURCE_BRANCH;
import static io.harness.vm.CIVMConstants.DRONE_TARGET_BRANCH;
import static io.harness.vm.CIVMConstants.NETWORK_ID;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.dependencies.CIServiceInfo;
import io.harness.beans.dependencies.DependencyElement;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.IntegrationStageConfig;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.RunsOnInfra;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.ci.buildstate.CodebaseUtils;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.logserviceclient.CILogServiceUtils;
import io.harness.ci.tiserviceclient.TIServiceUtils;
import io.harness.ci.utils.CIVmSecretEvaluator;
import io.harness.ci.utils.HostedVmSecretResolver;
import io.harness.ci.utils.InfrastructureUtils;
import io.harness.ci.utils.ValidationUtils;
import io.harness.connector.SecretSpecBuilder;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.delegate.beans.ci.vm.CIVmInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.dlite.DliteVmInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest;
import io.harness.delegate.beans.ci.vm.runner.SetupVmRequest;
import io.harness.delegate.beans.ci.vm.steps.VmServiceDependency;
import io.harness.delegate.task.citasks.vm.helper.StepExecutionHelper;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.logstreaming.LogStreamingHelper;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepUtils;
import io.harness.stoserviceclient.STOServiceUtils;
import io.harness.vm.VmExecuteStepUtils;
import io.harness.yaml.utils.NGVariablesUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class VmInitializeTaskParamsBuilder {
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject CILogServiceUtils logServiceUtils;

  @Inject HostedVmSecretResolver hostedVmSecretResolver;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject TIServiceUtils tiServiceUtils;
  @Inject STOServiceUtils stoServiceUtils;
  @Inject CodebaseUtils codebaseUtils;
  @Inject ConnectorUtils connectorUtils;
  @Inject CIVmSecretEvaluator ciVmSecretEvaluator;
  @Inject private CIFeatureFlagService featureFlagService;
  @Inject private VmInitializeUtils vmInitializeUtils;
  @Inject ValidationUtils validationUtils;

  @Inject SecretSpecBuilder secretSpecBuilder;

  @Inject StepExecutionHelper stepExecutionHelper;
  @Inject private VmExecuteStepUtils vmExecuteStepUtils;
  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 3;

  public DliteVmInitializeTaskParams getHostedVmInitializeTaskParams(
      InitializeStepInfo initializeStepInfo, Ambiance ambiance) {
    RunsOnInfra runsOnInfra = (RunsOnInfra) initializeStepInfo.getInfrastructure();
    String poolId = runsOnInfra.getSpec().getRunsOn();

    CIVmInitializeTaskParams params = getVmInitializeParams(initializeStepInfo, ambiance, poolId);
    SetupVmRequest setupVmRequest = convertHostedSetupParams(params);
    List<ExecuteStepRequest> services = new ArrayList<>();
    if (isNotEmpty(params.getServiceDependencies())) {
      for (VmServiceDependency serviceDependency : params.getServiceDependencies()) {
        services.add(vmExecuteStepUtils.convertService(serviceDependency, params).build());
      }
    }

    DliteVmInitializeTaskParams taskParams =
        DliteVmInitializeTaskParams.builder().setupVmRequest(setupVmRequest).services(services).build();
    hostedVmSecretResolver.resolve(ambiance, taskParams);
    return taskParams;
  }

  public CIVmInitializeTaskParams getDirectVmInitializeTaskParams(
      InitializeStepInfo initializeStepInfo, Ambiance ambiance) {
    Infrastructure infrastructure = initializeStepInfo.getInfrastructure();
    validateInfrastructure(infrastructure);

    VmPoolYaml vmPoolYaml = (VmPoolYaml) ((VmInfraYaml) infrastructure).getSpec();
    String poolId = getPoolName(vmPoolYaml);
    return getVmInitializeParams(initializeStepInfo, ambiance, poolId);
  }

  private CIVmInitializeTaskParams getVmInitializeParams(
      InitializeStepInfo initializeStepInfo, Ambiance ambiance, String poolId) {
    Infrastructure infrastructure = initializeStepInfo.getInfrastructure();
    String accountID = AmbianceUtils.getAccountId(ambiance);

    validateInfrastructure(infrastructure);

    IntegrationStageConfig integrationStageConfig = initializeStepInfo.getStageElementConfig();
    vmInitializeUtils.validateStageConfig(integrationStageConfig, accountID);

    OSType os = vmInitializeUtils.getOS(infrastructure);
    Map<String, String> volToMountPath =
        vmInitializeUtils.getVolumeToMountPath(integrationStageConfig.getSharedPaths(), os);
    String workDir = vmInitializeUtils.getWorkDir(os);

    String harnessImageConnectorRef = null;
    Optional<ParameterField<String>> optionalHarnessImageConnectorRef =
        InfrastructureUtils.getHarnessImageConnector(infrastructure);
    if (optionalHarnessImageConnectorRef.isPresent()) {
      harnessImageConnectorRef = optionalHarnessImageConnectorRef.get().getValue();
    }

    saveStageInfraDetails(ambiance, poolId, workDir, harnessImageConnectorRef, volToMountPath,
        initializeStepInfo.getInfrastructure().getType());
    StageDetails stageDetails = getStageDetails(ambiance);

    CIExecutionArgs ciExecutionArgs = CIExecutionArgs.builder()
                                          .runSequence(String.valueOf(ambiance.getMetadata().getRunSequence()))
                                          .executionSource(initializeStepInfo.getExecutionSource())
                                          .build();

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    ConnectorDetails gitConnector = codebaseUtils.getGitConnector(
        ngAccess, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone());
    Map<String, String> codebaseEnvVars = codebaseUtils.getCodebaseVars(ambiance, ciExecutionArgs);
    Map<String, String> gitEnvVars = codebaseUtils.getGitEnvVariables(gitConnector, initializeStepInfo.getCiCodebase());

    Map<String, String> envVars = new HashMap<>();
    envVars.putAll(codebaseEnvVars);
    envVars.putAll(gitEnvVars);

    Map<String, String> stageVars = getEnvironmentVariables(
        NGVariablesUtils.getMapOfVariables(initializeStepInfo.getVariables(), ambiance.getExpressionFunctorToken()));
    CIVmSecretEvaluator ciVmSecretEvaluator = CIVmSecretEvaluator.builder().build();
    Set<String> secrets = ciVmSecretEvaluator.resolve(stageVars, ngAccess, ambiance.getExpressionFunctorToken());
    envVars.putAll(stageVars);

    return CIVmInitializeTaskParams.builder()
        .poolID(poolId)
        .workingDir(workDir)
        .environment(envVars)
        .gitConnector(gitConnector)
        .stageRuntimeId(stageDetails.getStageRuntimeID())
        .accountID(accountID)
        .orgID(AmbianceUtils.getOrgIdentifier(ambiance))
        .projectID(AmbianceUtils.getProjectIdentifier(ambiance))
        .pipelineID(ambiance.getMetadata().getPipelineIdentifier())
        .stageID(stageDetails.getStageID())
        .buildID(String.valueOf(ambiance.getMetadata().getRunSequence()))
        .logKey(getLogKey(ambiance))
        .logStreamUrl(logServiceUtils.getLogServiceConfig().getBaseUrl())
        .logSvcToken(getLogSvcToken(accountID))
        .logSvcIndirectUpload(featureFlagService.isEnabled(FeatureName.CI_INDIRECT_LOG_UPLOAD, accountID))
        .tiUrl(tiServiceUtils.getTiServiceConfig().getBaseUrl())
        .tiSvcToken(getTISvcToken(accountID))
        .stoUrl(stoServiceUtils.getStoServiceConfig().getBaseUrl())
        .stoSvcToken(getSTOSvcToken(accountID))
        .secrets(new ArrayList<>(secrets))
        .volToMountPath(volToMountPath)
        .serviceDependencies(getServiceDependencies(ambiance, integrationStageConfig))
        .build();
  }

  private void validateInfrastructure(Infrastructure infrastructure) {
    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    if (infrastructure.getType() == Infrastructure.Type.RUNS_ON) {
      return;
    }

    if (((VmInfraYaml) infrastructure).getSpec() == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    VmInfraYaml vmInfraYaml = (VmInfraYaml) infrastructure;
    if (vmInfraYaml.getSpec().getType() != VmInfraSpec.Type.POOL) {
      throw new CIStageExecutionException(
          format("Invalid VM infrastructure spec type: %s", vmInfraYaml.getSpec().getType()));
    }
  }

  private String getPoolName(VmPoolYaml vmPoolYaml) {
    String poolName = vmPoolYaml.getSpec().getPoolName().getValue();
    if (isNotEmpty(poolName)) {
      return poolName;
    }

    String poolId = vmPoolYaml.getSpec().getIdentifier();
    if (isEmpty(poolId)) {
      throw new CIStageExecutionException("VM pool name should be set");
    }
    return poolId;
  }

  private void saveStageInfraDetails(Ambiance ambiance, String poolId, String workDir, String harnessImageConnectorRef,
      Map<String, String> volToMountPath, Infrastructure.Type infraType) {
    if (infraType == Infrastructure.Type.VM) {
      consumeSweepingOutput(ambiance,
          VmStageInfraDetails.builder()
              .poolId(poolId)
              .workDir(workDir)
              .volToMountPathMap(volToMountPath)
              .harnessImageConnectorRef(harnessImageConnectorRef)
              .build(),
          STAGE_INFRA_DETAILS);
    } else if (infraType == Infrastructure.Type.RUNS_ON) {
      consumeSweepingOutput(ambiance,
          DliteVmStageInfraDetails.builder()
              .poolId(poolId)
              .workDir(workDir)
              .volToMountPathMap(volToMountPath)
              .harnessImageConnectorRef(harnessImageConnectorRef)
              .build(),
          STAGE_INFRA_DETAILS);
    }
  }

  private StageDetails getStageDetails(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
    if (!optionalSweepingOutput.isFound()) {
      throw new CIStageExecutionException("Stage details sweeping output cannot be empty");
    }

    return (StageDetails) optionalSweepingOutput.getOutput();
  }

  private List<VmServiceDependency> getServiceDependencies(
      Ambiance ambiance, IntegrationStageConfig integrationStageConfig) {
    List<VmServiceDependency> serviceDependencies = new ArrayList<>();
    List<DependencyElement> dependencyElements = new ArrayList<>();
    if (integrationStageConfig.getServiceDependencies() != null
        && integrationStageConfig.getServiceDependencies().getValue() != null) {
      dependencyElements = integrationStageConfig.getServiceDependencies().getValue();
      validationUtils.validateVmInfraDependencies(dependencyElements);
    }
    if (isEmpty(dependencyElements)) {
      return serviceDependencies;
    }

    for (DependencyElement dependencyElement : dependencyElements) {
      if (dependencyElement == null) {
        continue;
      }

      if (dependencyElement.getDependencySpecType() instanceof CIServiceInfo) {
        serviceDependencies.add(
            getServiceDependency(ambiance, (CIServiceInfo) dependencyElement.getDependencySpecType()));
      }
    }
    return serviceDependencies;
  }

  private VmServiceDependency getServiceDependency(Ambiance ambiance, CIServiceInfo ciServiceInfo) {
    String image = RunTimeInputHandler.resolveStringParameter(
        "Image", "Service", ciServiceInfo.getIdentifier(), ciServiceInfo.getImage(), true);
    String connectorIdentifier = RunTimeInputHandler.resolveStringParameter(
        "connectorRef", "Service", ciServiceInfo.getIdentifier(), ciServiceInfo.getConnectorRef(), false);
    Map<String, String> envVars = resolveMapParameter(
        "envVariables", "Service", ciServiceInfo.getIdentifier(), ciServiceInfo.getEnvVariables(), false);
    Map<String, String> portBindings = RunTimeInputHandler.resolveMapParameter(
        "portBindings", "Service", ciServiceInfo.getIdentifier(), ciServiceInfo.getPortBindings(), false);

    String logKey = format("%s/serviceId:%s", getLogPrefix(ambiance), ciServiceInfo.getIdentifier());

    Set<String> secrets =
        ciVmSecretEvaluator.resolve(envVars, AmbianceUtils.getNgAccess(ambiance), ambiance.getExpressionFunctorToken());

    ConnectorDetails connectorDetails = null;
    if (!StringUtils.isEmpty(connectorIdentifier)) {
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorIdentifier);
    }
    return VmServiceDependency.builder()
        .name(ciServiceInfo.getName())
        .identifier(ciServiceInfo.getIdentifier())
        .image(image)
        .imageConnector(connectorDetails)
        .envVariables(envVars)
        .secrets(new ArrayList<>(secrets))
        .logKey(logKey)
        .portBindings(portBindings)
        .build();
  }

  private Map<String, String> getEnvironmentVariables(Map<String, Object> inputVariables) {
    if (isEmpty(inputVariables)) {
      return new HashMap<>();
    }
    Map<String, String> res = new LinkedHashMap<>();
    inputVariables.forEach((key, value) -> {
      if (value instanceof ParameterField) {
        ParameterField<?> parameterFieldValue = (ParameterField<?>) value;
        if (parameterFieldValue.getValue() == null) {
          throw new CIStageExecutionException(String.format("Env. variable [%s] value found to be null", key));
        }
        res.put(key, parameterFieldValue.getValue().toString());
      } else if (value instanceof String) {
        res.put(key, (String) value);
      } else {
        log.error(String.format(
            "Value other than String or ParameterField found for env. variable [%s]. value: [%s]", key, value));
      }
    });
    return res;
  }

  private String getLogSvcToken(String accountID) {
    RetryPolicy<Object> retryPolicy =
        getRetryPolicy(format("[Retrying failed call to fetch log service token attempt: {}"),
            format("Failed to fetch log service token after retrying {} times"));
    return Failsafe.with(retryPolicy).get(() -> logServiceUtils.getLogServiceToken(accountID));
  }

  private String getTISvcToken(String accountID) {
    // Make a call to the TI service and get back the token. We do not need TI service token for all steps,
    // so we can continue even if the service is down.
    try {
      return tiServiceUtils.getTIServiceToken(accountID);
    } catch (Exception e) {
      log.error("Could not call token endpoint for TI service", e);
    }

    return "";
  }

  private String getSTOSvcToken(String accountID) {
    // Make a call to the STO service and get back the token. We do not need STO service token for all steps,
    // so we can continue even if the service is down.
    try {
      return stoServiceUtils.getSTOServiceToken(accountID);
    } catch (Exception e) {
      log.error("Could not call token endpoint for STO service", e);
    }

    return "";
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }

  private <T extends ExecutionSweepingOutput> void consumeSweepingOutput(Ambiance ambiance, T value, String key) {
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(key));
    if (!optionalSweepingOutput.isFound()) {
      executionSweepingOutputResolver.consume(ambiance, key, value, StepOutcomeGroup.STAGE.name());
    }
  }

  private String getLogKey(Ambiance ambiance) {
    LinkedHashMap<String, String> logAbstractions = StepUtils.generateLogAbstractions(ambiance);
    return LogStreamingHelper.generateLogBaseKey(logAbstractions);
  }

  private String getLogPrefix(Ambiance ambiance) {
    LinkedHashMap<String, String> logAbstractions = StepUtils.generateLogAbstractions(ambiance, "STAGE");
    return LogStreamingHelper.generateLogBaseKey(logAbstractions);
  }

  private SetupVmRequest convertHostedSetupParams(CIVmInitializeTaskParams params) {
    Map<String, String> env = new HashMap<>();
    List<String> secrets = new ArrayList<>();
    if (isNotEmpty(params.getSecrets())) {
      secrets.addAll(params.getSecrets());
    }
    if (isNotEmpty(params.getEnvironment())) {
      env = params.getEnvironment();
    }

    if (params.getGitConnector() != null) {
      Map<String, SecretParams> secretVars = secretSpecBuilder.decryptGitSecretVariables(params.getGitConnector());
      for (Map.Entry<String, SecretParams> entry : secretVars.entrySet()) {
        String secret = new String(decodeBase64(entry.getValue().getValue()));
        env.put(entry.getKey(), secret);
        secrets.add(secret);
      }
    }

    SetupVmRequest.Config config = SetupVmRequest.Config.builder()
                                       .envs(env)
                                       .secrets(secrets)
                                       .network(SetupVmRequest.Network.builder().id(NETWORK_ID).build())
                                       .logConfig(SetupVmRequest.LogConfig.builder()
                                                      .url(params.getLogStreamUrl())
                                                      .token(params.getLogSvcToken())
                                                      .accountID(params.getAccountID())
                                                      .indirectUpload(params.isLogSvcIndirectUpload())
                                                      .build())
                                       .tiConfig(getTIConfig(params, env))
                                       .volumes(getVolumes(params.getVolToMountPath()))
                                       .build();
    return SetupVmRequest.builder()
        .id(params.getStageRuntimeId())
        //            .correlationID(taskId)
        .poolID(params.getPoolID())
        .config(config)
        .logKey(params.getLogKey())
        .build();
  }

  private List<SetupVmRequest.Volume> getVolumes(Map<String, String> volToMountPath) {
    List<SetupVmRequest.Volume> volumes = new ArrayList<>();
    if (isEmpty(volToMountPath)) {
      return volumes;
    }

    for (Map.Entry<String, String> entry : volToMountPath.entrySet()) {
      volumes.add(SetupVmRequest.Volume.builder()
                      .hostVolume(SetupVmRequest.HostVolume.builder()
                                      .id(entry.getKey())
                                      .name(entry.getKey())
                                      .path(entry.getValue())
                                      .build())
                      .build());
    }
    return volumes;
  }

  private SetupVmRequest.TIConfig getTIConfig(CIVmInitializeTaskParams params, Map<String, String> env) {
    return SetupVmRequest.TIConfig.builder()
        .url(params.getTiUrl())
        .token(params.getTiSvcToken())
        .accountID(params.getAccountID())
        .orgID(params.getOrgID())
        .projectID(params.getProjectID())
        .pipelineID(params.getPipelineID())
        .stageID(params.getStageID())
        .buildID(params.getBuildID())
        .repo(env.getOrDefault(DRONE_REMOTE_URL, ""))
        .sha(env.getOrDefault(DRONE_COMMIT_SHA, ""))
        .sourceBranch(env.getOrDefault(DRONE_SOURCE_BRANCH, ""))
        .targetBranch(env.getOrDefault(DRONE_TARGET_BRANCH, ""))
        .commitBranch(env.getOrDefault(DRONE_COMMIT_BRANCH, ""))
        .commitLink(env.getOrDefault(DRONE_COMMIT_LINK, ""))
        .build();
  }
}
