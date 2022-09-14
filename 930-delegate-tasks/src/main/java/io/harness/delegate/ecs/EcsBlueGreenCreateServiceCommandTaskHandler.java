package io.harness.delegate.ecs;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.ecs.EcsBlueGreenCreateServiceResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsInfraConfigHelper;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBlueGreenCreateServiceRequest;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenCreateServiceResponse;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.delegate.task.ecs.response.EcsRollingDeployResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;
import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import java.util.List;

import static java.lang.String.format;
import static software.wings.beans.LogHelper.color;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsBlueGreenCreateServiceCommandTaskHandler extends EcsCommandTaskNGHandler {
    @Inject private EcsTaskHelperBase ecsTaskHelperBase;
    @Inject private EcsInfraConfigHelper ecsInfraConfigHelper;
    @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;
    @Inject private AwsNgConfigMapper awsNgConfigMapper;
    private EcsInfraConfig ecsInfraConfig;
    private long timeoutInMillis;

    @Override
    protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest, ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
        if (!(ecsCommandRequest instanceof EcsBlueGreenCreateServiceRequest)) {
            throw new InvalidArgumentsException(Pair.of("ecsCommandRequest", "Must be instance of EcsBlueGreenCreateServiceRequest"));
        }

        EcsBlueGreenCreateServiceRequest ecsBlueGreenCreateServiceRequest = (EcsBlueGreenCreateServiceRequest)
                ecsCommandRequest;

        timeoutInMillis = ecsBlueGreenCreateServiceRequest.getTimeoutIntervalInMin() * 60000;
        ecsInfraConfig = ecsBlueGreenCreateServiceRequest.getEcsInfraConfig();

        LogCallback deployLogCallback = ecsTaskHelperBase.getLogCallback(
                iLogStreamingTaskClient, EcsCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);

        try{
            AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());
            String ecsServiceDefinitionManifestContent = ecsBlueGreenCreateServiceRequest.getEcsServiceDefinitionManifestContent();
            List<String> ecsScalableTargetManifestContentList =
                    ecsBlueGreenCreateServiceRequest.getEcsScalableTargetManifestContentList();
            List<String> ecsScalingPolicyManifestContentList =
                    ecsBlueGreenCreateServiceRequest.getEcsScalingPolicyManifestContentList();
            String ecsTaskDefinitionManifestContent = ecsBlueGreenCreateServiceRequest.getEcsTaskDefinitionManifestContent();

            RegisterTaskDefinitionRequest.Builder registerTaskDefinitionRequestBuilder =
                    ecsCommandTaskHelper.parseYamlAsObject(
                            ecsTaskDefinitionManifestContent, RegisterTaskDefinitionRequest.serializableBuilderClass());
            RegisterTaskDefinitionRequest registerTaskDefinitionRequest = registerTaskDefinitionRequestBuilder.build();
            deployLogCallback.saveExecutionLog(
                    format("Creating Task Definition with family %s %n", registerTaskDefinitionRequest.family()), LogLevel.INFO);

            RegisterTaskDefinitionResponse registerTaskDefinitionResponse = ecsCommandTaskHelper.createTaskDefinition(
                    registerTaskDefinitionRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
            TaskDefinition taskDefinition = registerTaskDefinitionResponse.taskDefinition();
            String taskDefinitionName = taskDefinition.family() + ":" + taskDefinition.revision();
            String taskDefinitionArn = taskDefinition.taskDefinitionArn();

            deployLogCallback.saveExecutionLog(
                    format("Created Task Definition %s with Arn %s..%n", taskDefinitionName, taskDefinitionArn), LogLevel.INFO);

            // find target group arn from stage listener and stage listener rule arn
            String targetGroupArn = ecsCommandTaskHelper.getTargetGroupArnFromLoadBalancer(ecsInfraConfig,
                    ecsBlueGreenCreateServiceRequest.getStageListenerArn(),
                    ecsBlueGreenCreateServiceRequest.getStageListenerRuleArn(), ecsBlueGreenCreateServiceRequest.getLoadBalancer(),
                    awsInternalConfig);

            String serviceName = ecsCommandTaskHelper.createStageService(ecsServiceDefinitionManifestContent, ecsScalableTargetManifestContentList,
                    ecsScalingPolicyManifestContentList, ecsInfraConfig, deployLogCallback, timeoutInMillis,
                    ecsBlueGreenCreateServiceRequest, taskDefinitionArn, targetGroupArn);

            EcsBlueGreenCreateServiceResult ecsBlueGreenCreateServiceResult =
                    EcsBlueGreenCreateServiceResult.builder()
                            .region(ecsInfraConfig.getRegion())
                            .ecsTasks(ecsCommandTaskHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(),
                                    ecsInfraConfig.getCluster(), serviceName, ecsInfraConfig.getRegion()))
                            .targetGroupArn(targetGroupArn)
                            .isNewServiceCreated(true)
                            .serviceName(serviceName)
                            .loadBalancer(ecsBlueGreenCreateServiceRequest.getLoadBalancer())
                            .listenerArn(ecsBlueGreenCreateServiceRequest.getStageListenerArn())
                            .listenerRuleArn(ecsBlueGreenCreateServiceRequest.getStageListenerRuleArn())
                            .build();
            EcsBlueGreenCreateServiceResponse ecsBlueGreenCreateServiceResponse = EcsBlueGreenCreateServiceResponse.builder()
                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                    .ecsBlueGreenCreateServiceResult(ecsBlueGreenCreateServiceResult)
                    .build();
            deployLogCallback.saveExecutionLog(color(format("%n Deployment Successful."), LogColor.Green, LogWeight.Bold),
                    LogLevel.INFO, CommandExecutionStatus.SUCCESS);

            return ecsBlueGreenCreateServiceResponse;
        }
        catch (Exception e) {
            deployLogCallback.saveExecutionLog(color(format("%n Deployment Failed."), LogColor.Red, LogWeight.Bold),
                    LogLevel.ERROR, CommandExecutionStatus.FAILURE);
            throw new EcsNGException(e);
        }
    }
}
