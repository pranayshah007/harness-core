package io.harness.cdng.aws.sam;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.sam.AwsSamEntityHelper;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.aws.sam.AwsSamInfraConfig;
import io.harness.delegate.task.aws.sam.AwsSamArtifactConfig;
import io.harness.delegate.task.aws.sam.AwsSamCommandType;
import io.harness.delegate.task.aws.sam.AwsSamDeployConfig;
import io.harness.delegate.task.aws.sam.request.AwsSamDeployRequest;
import io.harness.ng.core.NGAccess;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.TaskRequestsUtils;
import software.wings.beans.TaskType;

import java.util.Optional;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class AwsSamStepHelper extends CDStepHelper {

  @Inject
  private AwsSamEntityHelper awsSamEntityHelper;

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  public TaskChainResponse startChainLinkDeploy(
          Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    return executeAwsSamDeployTask(ambiance, stepElementParameters, inputPackage);
  }

  public TaskChainResponse executeAwsSamDeployTask(Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {

    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    AwsSamDeployStepParameters awsSamDeployStepParameters =
            (AwsSamDeployStepParameters) stepElementParameters.getSpec();

    final String accountId = AmbianceUtils.getAccountId(ambiance);

    // Get SAM Artifact Details
    AwsSamArtifactConfig awsSamArtifactConfig = null;

    Optional<ArtifactsOutcome> artifactsOutcome = getArtifactsOutcome(ambiance);

    if (artifactsOutcome.isPresent()) {
      if (artifactsOutcome.get().getPrimary() != null) {
        awsSamArtifactConfig = getArtifactConfig(artifactsOutcome.get().getPrimary(), ambiance);
      }
    }

    // GET SAM Infra Config
    AwsSamInfraConfig awsSamInfraConfig = getAwsSamInfraConfig(infrastructureOutcome, ambiance);

    AwsSamDeployConfig awsSamDeployConfig = AwsSamDeployConfig.builder()
            .stackName(awsSamDeployStepParameters.getStackName().getValue())
            .samCliPollDelay(awsSamDeployStepParameters.getSamCliPollDelay().getValue())
            .deployCommandOptions(awsSamDeployStepParameters.getDeployCommandOptions().getValue())
            .build();

    AwsSamDeployRequest awsSamDeployRequest =  AwsSamDeployRequest.builder()
            .accountId(accountId)
            .awsSamArtifactConfig(awsSamArtifactConfig)
            .awsSamDeployConfig(awsSamDeployConfig)
            .awsSamInfraConfig(awsSamInfraConfig)
            .awsSamCommandType(AwsSamCommandType.AWS_SAM_DEPLOY)
            .build();

    // Queue AWS SAM Delegate Task
    TaskData taskData = TaskData.builder()
            .parameters(new Object[] {awsSamDeployRequest})
            .taskType(TaskType.AWS_SAM_DEPLOY_TASK.name())
            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
            .async(true)
            .build();


    String taskName =
            TaskType.AWS_SAM_DEPLOY_TASK.getDisplayName();

    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
            referenceFalseKryoSerializer, awsSamDeployStepParameters.getCommandUnits(), taskName,
            TaskSelectorYaml.toTaskSelector(
                    emptyIfNull(getParameterFieldValue(awsSamDeployStepParameters.getDelegateSelectors()))),
            stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
            .taskRequest(taskRequest)
            .chainEnd(true)
            .build();
  }

  public Optional<ArtifactsOutcome> getArtifactsOutcome(Ambiance ambiance) {
    OptionalOutcome artifactsOutcomeOption = outcomeService.resolveOptional(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    if (artifactsOutcomeOption.isFound()) {
      ArtifactsOutcome artifactsOutcome = (ArtifactsOutcome) artifactsOutcomeOption.getOutcome();
      return Optional.of(artifactsOutcome);
    }
    return Optional.empty();
  }

  public AwsSamArtifactConfig getArtifactConfig(ArtifactOutcome artifactOutcome, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return awsSamEntityHelper.getAwsSamArtifactConfig(artifactOutcome, ngAccess);
  }

  public AwsSamInfraConfig getAwsSamInfraConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return awsSamEntityHelper.getAwsSamInfraConfig(infrastructure, ngAccess);
  }

}
