package io.harness.delegate.ecs;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.ecs.EcsBlueGreenPrepareRollbackDataResult;
import io.harness.delegate.beans.ecs.EcsMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBlueGreenPrepareRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenPrepareRollbackDataResponse;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.Service;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsBlueGreenPrepareRollbackCommandTaskHandler extends EcsCommandTaskNGHandler {
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  private EcsInfraConfig ecsInfraConfig;
  private long timeoutInMillis;

  @Override
  protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(ecsCommandRequest instanceof EcsBlueGreenPrepareRollbackRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("ecsCommandRequest", "Must be instance of EcsBlueGreenPrepareRollbackRequest"));
    }

    EcsBlueGreenPrepareRollbackRequest ecsBlueGreenPrepareRollbackRequest =
        (EcsBlueGreenPrepareRollbackRequest) ecsCommandRequest;

    timeoutInMillis = ecsBlueGreenPrepareRollbackRequest.getTimeoutIntervalInMin() * 60000;
    ecsInfraConfig = ecsBlueGreenPrepareRollbackRequest.getEcsInfraConfig();

    LogCallback prepareRollbackDataLogCallback = ecsTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, EcsCommandUnitConstants.prepareRollbackData.toString(), true, commandUnitsProgress);

    try {
      AwsInternalConfig awsInternalConfig =
          awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());

      // Get Ecs Service Name
      String ecsServiceDefinitionManifestContent =
          ecsBlueGreenPrepareRollbackRequest.getEcsServiceDefinitionManifestContent();
      CreateServiceRequest createServiceRequest =
          ecsCommandTaskHelper
              .parseYamlAsObject(ecsServiceDefinitionManifestContent, CreateServiceRequest.serializableBuilderClass())
              .build();

      // Get targetGroup Arn
      String targetGroupArn = ecsCommandTaskHelper.getTargetGroupArnFromLoadBalancer(ecsInfraConfig,
          ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getProdListenerArn(),
          ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getProdListenerRuleArn(),
          ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getLoadBalancer(), awsInternalConfig);

      Optional<String> optionalServiceName = ecsCommandTaskHelper.getBlueVersionServiceName(
          createServiceRequest.serviceName() + EcsCommandTaskNGHelper.DELIMITER, ecsInfraConfig);
      if (!optionalServiceName.isPresent() || EmptyPredicate.isEmpty(optionalServiceName.get())) {
        // If no blue version service found
        return getFirstTimeDeploymentResponse(
            prepareRollbackDataLogCallback, targetGroupArn, ecsBlueGreenPrepareRollbackRequest);
      }
      String serviceName = optionalServiceName.get();

      prepareRollbackDataLogCallback.saveExecutionLog(
          format("Fetching Service Definition Details for Service %s..", serviceName), LogLevel.INFO);

      // Describe ecs service and get service details
      Optional<Service> optionalService = ecsCommandTaskHelper.describeService(
          ecsInfraConfig.getCluster(), serviceName, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

      if (optionalService.isPresent()
          && ecsCommandTaskHelper.isServiceActive(optionalService.get())) { // If service exists
        Service service = optionalService.get();

        // Get createServiceRequestBuilderString from service
        String createServiceRequestBuilderString = EcsMapper.createCreateServiceRequestFromService(service);
        prepareRollbackDataLogCallback.saveExecutionLog(
            format("Fetched Service Definition Details for Service %s", serviceName), LogLevel.INFO);

        // Get registerScalableTargetRequestBuilderStrings if present
        List<String> registerScalableTargetRequestBuilderStrings = ecsCommandTaskHelper.getScalableTargetsAsString(
            prepareRollbackDataLogCallback, serviceName, service, ecsInfraConfig);

        // Get putScalingPolicyRequestBuilderStrings if present
        List<String> registerScalingPolicyRequestBuilderStrings = ecsCommandTaskHelper.getScalingPoliciesAsString(
            prepareRollbackDataLogCallback, serviceName, service, ecsInfraConfig);

        EcsBlueGreenPrepareRollbackDataResult ecsBlueGreenPrepareRollbackDataResult =
            EcsBlueGreenPrepareRollbackDataResult.builder()
                .createServiceRequestBuilderString(createServiceRequestBuilderString)
                .registerScalableTargetRequestBuilderStrings(registerScalableTargetRequestBuilderStrings)
                .registerScalingPolicyRequestBuilderStrings(registerScalingPolicyRequestBuilderStrings)
                .listenerArn(ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getProdListenerArn())
                .loadBalancer(ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getLoadBalancer())
                .listenerRuleArn(ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getProdListenerRuleArn())
                .targetGroupArn(targetGroupArn)
                .isFirstDeployment(false)
                .serviceName(serviceName)
                .build();
        EcsBlueGreenPrepareRollbackDataResponse ecsBlueGreenPrepareRollbackDataResponse =
            EcsBlueGreenPrepareRollbackDataResponse.builder()
                .ecsBlueGreenPrepareRollbackDataResult(ecsBlueGreenPrepareRollbackDataResult)
                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                .build();
        prepareRollbackDataLogCallback.saveExecutionLog(
            "Preparing Rollback Data complete", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        return ecsBlueGreenPrepareRollbackDataResponse;
      } else { // If service doesn't exist
        return getFirstTimeDeploymentResponse(
            prepareRollbackDataLogCallback, targetGroupArn, ecsBlueGreenPrepareRollbackRequest);
      }
    } catch (Exception e) {
      prepareRollbackDataLogCallback.saveExecutionLog(
          color(format("%n Deployment Failed."), LogColor.Red, LogWeight.Bold), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
      throw new EcsNGException(e);
    }
  }

  private EcsBlueGreenPrepareRollbackDataResponse getFirstTimeDeploymentResponse(LogCallback logCallback,
      String targetGroupArn, EcsBlueGreenPrepareRollbackRequest ecsBlueGreenPrepareRollbackRequest) {
    logCallback.saveExecutionLog("Blue version of Service doesn't exist. Skipping Prepare Rollback Data..",
        LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    // Send EcsBlueGreenPrepareRollbackDataResult with isFirstDeployment as true
    EcsBlueGreenPrepareRollbackDataResult ecsBlueGreenPrepareRollbackDataResult =
        EcsBlueGreenPrepareRollbackDataResult.builder()
            .isFirstDeployment(true)
            .targetGroupArn(targetGroupArn)
            .listenerArn(ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getProdListenerArn())
            .loadBalancer(ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getLoadBalancer())
            .listenerRuleArn(ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getProdListenerRuleArn())
            .build();

    return EcsBlueGreenPrepareRollbackDataResponse.builder()
        .ecsBlueGreenPrepareRollbackDataResult(ecsBlueGreenPrepareRollbackDataResult)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }
}
