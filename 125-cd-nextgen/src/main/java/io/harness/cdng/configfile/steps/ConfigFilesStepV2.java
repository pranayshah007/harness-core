package io.harness.cdng.configfile.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.configfile.ConfigFileAttributes;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.configfile.mapper.ConfigFileOutcomeMapper;
import io.harness.cdng.configfile.validator.IndividualConfigFileStepValidator;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
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
  @Inject private CDStepHelper cdStepHelper;

  @Override
  public Class<EmptyStepParameters> getStepParametersClass() {
    return EmptyStepParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage,
      PassThroughData passThroughData) {
    final Optional<NGServiceV2InfoConfig> serviceOptional = cdStepHelper.fetchServiceConfigFromSweepingOutput(ambiance);
    if (serviceOptional.isEmpty()) {
      return StepResponse.builder().status(Status.SKIPPED).build();
    }
    final NGServiceV2InfoConfig service = serviceOptional.get();

    if (service.getServiceDefinition() == null) {
      log.info(
          "no service definition found for service " + service.getIdentifier() + ". skipping the config files step");
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

    final List<ConfigFileWrapper> configFiles = service.getServiceDefinition().getServiceSpec().getConfigFiles();

    if (EmptyPredicate.isEmpty(configFiles)) {
      log.info("no config files found for service " + service.getIdentifier() + ". skipping the config files step");
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

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

    sweepingOutputService.consume(
        ambiance, OutcomeExpressionConstants.CONFIG_FILES, configFilesOutcome, StepCategory.STAGE.name());

    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.CONFIG_FILES)
                         .outcome(configFilesOutcome)
                         .group(StepCategory.STAGE.name())
                         .build())
        .build();
  }
}
