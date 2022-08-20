package io.harness.cdng.configfile.steps;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;

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
import io.harness.connector.services.ConnectorService;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
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
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ConfigFilesStepV2 implements SyncExecutable<EmptyStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.CONFIG_FILES_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private ConfigFileStepUtils configFileStepUtils;

  @Override
  public Class<EmptyStepParameters> getStepParametersClass() {
    return EmptyStepParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage,
      PassThroughData passThroughData) {
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
      configFileStepUtils.verifyConfigFileReference(identifier, spec, ambiance);
      configFilesOutcome.put(identifier, ConfigFileOutcomeMapper.toConfigFileOutcome(identifier, i, spec));
    }

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
