package io.harness.cdng.configfile.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.configfile.ConfigFileAttributes;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.configfile.mapper.ConfigFileOutcomeMapper;
import io.harness.cdng.configfile.validator.IndividualConfigFileStepValidator;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.service.steps.ServiceStepV3;
import io.harness.cdng.service.steps.ServiceSweepingOutput;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ConfigFilesStepV2 extends AbstractConfigFileStep implements SyncExecutable<EmptyStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.CONFIG_FILES_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private CDExpressionResolver cdExpressionResolver;

  @Override
  public Class<EmptyStepParameters> getStepParametersClass() {
    return EmptyStepParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage,
      PassThroughData passThroughData) {
    try {
      ServiceSweepingOutput serviceSweepingOutput = (ServiceSweepingOutput) sweepingOutputService.resolve(
          ambiance, RefObjectUtils.getOutcomeRefObject(ServiceStepV3.SERVICE_SWEEPING_OUTPUT));
      NGServiceConfig ngServiceConfig = null;
      if (serviceSweepingOutput != null) {
        try {
          ngServiceConfig = YamlUtils.read(serviceSweepingOutput.getFinalServiceYaml(), NGServiceConfig.class);
        } catch (IOException e) {
          // Todo:(yogesh) handle exception
          throw new RuntimeException(e);
        }
      }

      if (ngServiceConfig == null || ngServiceConfig.getNgServiceV2InfoConfig() == null) {
        log.info("No service configuration found");
        return StepResponse.builder().status(Status.SKIPPED).build();
      }

      final List<ConfigFileWrapper> configFiles =
          ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec().getConfigFiles();

      final ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
      for (int i = 0; i < configFiles.size(); i++) {
        ConfigFileWrapper file = configFiles.get(i);
        ConfigFileAttributes spec = file.getConfigFile().getSpec();
        String identifier = file.getConfigFile().getIdentifier();
        cdExpressionResolver.updateStoreConfigExpressions(ambiance, spec.getStore().getValue());
        IndividualConfigFileStepValidator.validateConfigFileAttributes(identifier, spec, true);
        verifyConfigFileReference(identifier, spec, ambiance);
        configFilesOutcome.put(identifier, ConfigFileOutcomeMapper.toConfigFileOutcome(identifier, i + 1, spec));
      }

      return StepResponse.builder()
          .status(Status.SUCCEEDED)
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name(OutcomeExpressionConstants.CONFIG_FILES)
                           .outcome(configFilesOutcome)
                           .group(StepCategory.STAGE.name())
                           .build())
          .build();
    } catch (Exception ex) {
      log.error("Exception occurred in config files step v2", ex);
      return StepResponse.builder()
          .status(Status.FAILED)
          .failureInfo(
              FailureInfo.newBuilder()
                  .addFailureData(0, FailureData.newBuilder().addFailureTypes(FailureType.APPLICATION_FAILURE).build())
                  .build())
          .build();
    }
  }
}
