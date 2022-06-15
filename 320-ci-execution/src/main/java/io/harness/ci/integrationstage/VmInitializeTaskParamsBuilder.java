/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameter;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

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
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.ci.utils.ValidationUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.CIVmInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.steps.VmServiceDependency;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.ff.CIFeatureFlagService;
import io.harness.logserviceclient.CILogServiceUtils;
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
import io.harness.stateutils.buildstate.CodebaseUtils;
import io.harness.stateutils.buildstate.ConnectorUtils;
import io.harness.steps.StepUtils;
import io.harness.stoserviceclient.STOServiceUtils;
import io.harness.tiserviceclient.TIServiceUtils;
import io.harness.util.CIVmSecretEvaluator;
import io.harness.yaml.utils.NGVariablesUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject TIServiceUtils tiServiceUtils;
  @Inject STOServiceUtils stoServiceUtils;
  @Inject CodebaseUtils codebaseUtils;
  @Inject ConnectorUtils connectorUtils;
  @Inject CIVmSecretEvaluator ciVmSecretEvaluator;
  @Inject private CIFeatureFlagService featureFlagService;
  @Inject private VmInitializeUtils vmInitializeUtils;

  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 3;

  public CIVmInitializeTaskParams getVmInitializeTaskParams(
      InitializeStepInfo initializeStepInfo, Ambiance ambiance, String logPrefix) {
    Infrastructure infrastructure = initializeStepInfo.getInfrastructure();
    String accountID = AmbianceUtils.getAccountId(ambiance);

    validateInfrastructure(infrastructure);

    VmPoolYaml vmPoolYaml = (VmPoolYaml) ((VmInfraYaml) infrastructure).getSpec();
    IntegrationStageConfig integrationStageConfig = initializeStepInfo.getStageElementConfig();
    vmInitializeUtils.validateStageConfig(integrationStageConfig, accountID);

    String poolId = getPoolName(vmPoolYaml);
    OSType os = vmInitializeUtils.getOS(infrastructure);
    Map<String, String> volToMountPath =
        vmInitializeUtils.getVolumeToMountPath(integrationStageConfig.getSharedPaths(), os);
    String workDir = vmInitializeUtils.getWorkDir(os);

    saveStageInfraDetails(
        ambiance, poolId, workDir, vmPoolYaml.getSpec().getHarnessImageConnectorRef().getValue(), volToMountPath);
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
    if (infrastructure == null || ((VmInfraYaml) infrastructure).getSpec() == null) {
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
      Map<String, String> volToMountPath) {
    consumeSweepingOutput(ambiance,
        VmStageInfraDetails.builder()
            .poolId(poolId)
            .workDir(workDir)
            .volToMountPathMap(volToMountPath)
            .harnessImageConnectorRef(harnessImageConnectorRef)
            .build(),
        STAGE_INFRA_DETAILS);
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
      ValidationUtils.validateVmInfraDependencies(dependencyElements);
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
}
