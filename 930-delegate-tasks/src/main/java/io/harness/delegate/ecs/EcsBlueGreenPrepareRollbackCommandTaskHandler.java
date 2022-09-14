package io.harness.delegate.ecs;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.ecs.EcsBlueGreenPrepareRollbackDataResult;
import io.harness.delegate.beans.ecs.EcsMapper;
import io.harness.delegate.beans.ecs.EcsPrepareRollbackDataResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBlueGreenPrepareRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.request.EcsPrepareRollbackDataRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenPrepareRollbackDataResponse;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.delegate.task.ecs.response.EcsPrepareRollbackDataResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.Service;
import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static software.wings.beans.LogHelper.color;

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
                                                     ILogStreamingTaskClient iLogStreamingTaskClient,
                                                     CommandUnitsProgress commandUnitsProgress) throws Exception {
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
            AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());

            // Get Ecs Service Name
            String ecsServiceDefinitionManifestContent = ecsBlueGreenPrepareRollbackRequest.getEcsServiceDefinitionManifestContent();
            CreateServiceRequest createServiceRequest = ecsCommandTaskHelper.parseYamlAsObject(
                    ecsServiceDefinitionManifestContent, CreateServiceRequest.serializableBuilderClass()).build();

            // Get targetGroup Arn
            String targetGroupArn = ecsCommandTaskHelper.getTargetGroupArnFromLoadBalancer(ecsInfraConfig,
                    ecsBlueGreenPrepareRollbackRequest.getProdListener(), ecsBlueGreenPrepareRollbackRequest.getProdListenerRuleArn(),
                    ecsBlueGreenPrepareRollbackRequest.getLoadBalancer(), awsInternalConfig);

            String serviceName = ecsCommandTaskHelper.getBlueVersionServiceName(createServiceRequest.serviceName()+EcsCommandTaskNGHelper.DELIMITER,
                    ecsInfraConfig);
            if(EmptyPredicate.isEmpty(serviceName)) {
                // If no blue version service found
                return getFirstTimeDeploymentResponse(prepareRollbackDataLogCallback, targetGroupArn, serviceName);
            }

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
                                .listenerArn(ecsBlueGreenPrepareRollbackRequest.getProdListener())
                                .loadBalancer(ecsBlueGreenPrepareRollbackRequest.getLoadBalancer())
                                .listenerRuleArn(ecsBlueGreenPrepareRollbackRequest.getProdListenerRuleArn())
                                .targetGroupArn(targetGroupArn)
                                .isFirstDeployment(false)
                                .build();
                EcsBlueGreenPrepareRollbackDataResponse ecsBlueGreenPrepareRollbackDataResponse =
                        EcsBlueGreenPrepareRollbackDataResponse.builder()
                                .ecsBlueGreenPrepareRollbackDataResult(ecsBlueGreenPrepareRollbackDataResult)
                                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                .build();
                prepareRollbackDataLogCallback.saveExecutionLog(
                        "Preparing Rollback Data complete", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
                return ecsBlueGreenPrepareRollbackDataResponse;
            }
            else { // If service doesn't exist
                return getFirstTimeDeploymentResponse(prepareRollbackDataLogCallback, targetGroupArn, serviceName);
            }
        }
        catch (Exception e) {
            prepareRollbackDataLogCallback.saveExecutionLog(color(format("%n Deployment Failed."), LogColor.Red, LogWeight.Bold),
                    LogLevel.ERROR, CommandExecutionStatus.FAILURE);
            throw new EcsNGException(e);
        }
    }

    private EcsBlueGreenPrepareRollbackDataResponse getFirstTimeDeploymentResponse(LogCallback logCallback,
                                                                                   String targetGroupArn, String serviceName) {
        logCallback.saveExecutionLog(
                format("Service %s doesn't exist. Skipping Prepare Rollback Data..", serviceName), LogLevel.INFO,
                CommandExecutionStatus.SUCCESS);

        // Send EcsBlueGreenPrepareRollbackDataResult with isFirstDeployment as true
        EcsBlueGreenPrepareRollbackDataResult ecsBlueGreenPrepareRollbackDataResult =
                EcsBlueGreenPrepareRollbackDataResult.builder()
                        .isFirstDeployment(true)
                        .serviceName(serviceName)
                        .targetGroupArn(targetGroupArn)
                        .build();

        return EcsBlueGreenPrepareRollbackDataResponse.builder()
                .ecsBlueGreenPrepareRollbackDataResult(ecsBlueGreenPrepareRollbackDataResult)
                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                .build();

    }
}
