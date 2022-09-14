package io.harness.delegate.ecs;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsInfraConfigHelper;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBlueGreenCreateServiceRequest;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.request.EcsRollingDeployRequest;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;

import java.util.List;

import static java.lang.String.format;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsBlueGreenCreateServiceCommandTaskHandler extends EcsCommandTaskNGHandler {
    @Inject private EcsTaskHelperBase ecsTaskHelperBase;
    @Inject private EcsInfraConfigHelper ecsInfraConfigHelper;
    @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;
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
            String ecsTaskDefinitionManifestContent = ecsBlueGreenCreateServiceRequest.getEcsTaskDefinitionManifestContent();
            String ecsServiceDefinitionManifestContent = ecsBlueGreenCreateServiceRequest.getEcsServiceDefinitionManifestContent();
            List<String> ecsScalableTargetManifestContentList =
                    ecsBlueGreenCreateServiceRequest.getEcsScalableTargetManifestContentList();
            List<String> ecsScalingPolicyManifestContentList =
                    ecsBlueGreenCreateServiceRequest.getEcsScalingPolicyManifestContentList();

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

            ecsCommandTaskHelper.createStageService(ecsServiceDefinitionManifestContent, ecsScalableTargetManifestContentList,
                    ecsScalingPolicyManifestContentList, ecsInfraConfig, deployLogCallback, timeoutInMillis,
                    ecsBlueGreenCreateServiceRequest, taskDefinitionArn);
        }
        catch (Exception e) {

        }

        return null;
    }
}
