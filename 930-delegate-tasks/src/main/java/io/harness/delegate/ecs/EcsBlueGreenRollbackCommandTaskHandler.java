package io.harness.delegate.ecs;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.ecs.EcsBlueGreenRollbackResult.EcsBlueGreenRollbackResultBuilder;

import io.harness.delegate.beans.ecs.EcsBlueGreenRollbackResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBlueGreenRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenRollbackResponse;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
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
import software.amazon.awssdk.services.ecs.model.ServiceEvent;
import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;
import static software.wings.beans.LogHelper.color;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsBlueGreenRollbackCommandTaskHandler extends EcsCommandTaskNGHandler {
    @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;
    @Inject private EcsTaskHelperBase ecsTaskHelperBase;
    @Inject private AwsNgConfigMapper awsNgConfigMapper;
    private EcsInfraConfig ecsInfraConfig;
    private long timeoutInMillis;


    @Override
    protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest, ILogStreamingTaskClient
            iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
        if (!(ecsCommandRequest instanceof EcsBlueGreenRollbackRequest)) {
            throw new InvalidArgumentsException(Pair.of("ecsCommandRequest", "Must be instance of EcsBlueGreenRollbackRequest"));
        }
        EcsBlueGreenRollbackRequest ecsBlueGreenRollbackRequest = (EcsBlueGreenRollbackRequest)
                ecsCommandRequest;

        timeoutInMillis = ecsBlueGreenRollbackRequest.getTimeoutIntervalInMin() * 60000;
        ecsInfraConfig = ecsBlueGreenRollbackRequest.getEcsInfraConfig();

        LogCallback rollbackCallback = ecsTaskHelperBase.getLogCallback(
                iLogStreamingTaskClient, EcsCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);

        try{
            AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());
            if(!ecsBlueGreenRollbackRequest.isTargetShiftStarted()) {
                // failed after ecs blue green create service step and before ecs blue green swap target groups step
                // delete new service created without steady state wait
                ecsCommandTaskHelper.deleteService(ecsBlueGreenRollbackRequest.getNewServiceName(),
                        ecsBlueGreenRollbackRequest.getEcsInfraConfig().getCluster(),
                        ecsBlueGreenRollbackRequest.getEcsInfraConfig().getRegion(),
                        ecsBlueGreenRollbackRequest.getEcsInfraConfig().getAwsConnectorDTO());
            }
            else {
                // failed during ecs blue green swap target groups step or after it
                ecsBlueGreenRollback(ecsBlueGreenRollbackRequest, rollbackCallback, awsInternalConfig);
            }
            EcsBlueGreenRollbackResultBuilder ecsBlueGreenRollbackResultBuilder =
                    EcsBlueGreenRollbackResult.builder()
                            .region(ecsBlueGreenRollbackRequest.getEcsInfraConfig().getRegion())
                            .loadBalancer(ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getLoadBalancer())
                            .prodListenerArn(ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdListenerArn())
                            .prodListenerRuleArn(ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdListenerRuleArn())
                            .stageListenerArn(ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageListenerArn())
                            .stageListenerRuleArn(ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageListenerRuleArn());

            if(ecsBlueGreenRollbackRequest.isFirstDeployment()) {
                // first deployment case
                ecsBlueGreenRollbackResultBuilder.isFirstDeployment(true);
            }
            else {
                ecsBlueGreenRollbackResultBuilder.ecsTasks(ecsCommandTaskHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(),
                        ecsInfraConfig.getCluster(), ecsBlueGreenRollbackRequest.getOldServiceName(),
                        ecsInfraConfig.getRegion()));
            }
                ecsBlueGreenRollbackResultBuilder.prodTargetGroupArn(ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageTargetGroupArn());
                ecsBlueGreenRollbackResultBuilder.stageTargetGroupArn(ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdTargetGroupArn());
            return EcsBlueGreenRollbackResponse.builder()
                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                    .ecsBlueGreenRollbackResult(ecsBlueGreenRollbackResultBuilder.build())
                    .build();
        }
        catch (Exception e) {
            rollbackCallback.saveExecutionLog(color(format("%n Blue Green Rollback Failed."), LogColor.Red, LogWeight.Bold),
                    LogLevel.ERROR, CommandExecutionStatus.FAILURE);
            throw new EcsNGException(e);
        }
    }

    private void ecsBlueGreenRollback(EcsBlueGreenRollbackRequest ecsBlueGreenRollbackRequest,
                                                         LogCallback rollbackLog, AwsInternalConfig awsInternalConfig) {
        if(ecsBlueGreenRollbackRequest.isFirstDeployment()) {
            //swap target group with correct listener rule arns
            ecsCommandTaskHelper.swapTargetGroups(ecsInfraConfig, rollbackLog,
                    ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig(), awsInternalConfig);
            // delete new service created without steady state wait
            ecsCommandTaskHelper.deleteService(ecsBlueGreenRollbackRequest.getNewServiceName(),
                    ecsBlueGreenRollbackRequest.getEcsInfraConfig().getCluster(),
                    ecsBlueGreenRollbackRequest.getEcsInfraConfig().getRegion(),
                    ecsBlueGreenRollbackRequest.getEcsInfraConfig().getAwsConnectorDTO());
        }
        else {
            CreateServiceRequest createServiceRequest = ecsCommandTaskHelper.parseYamlAsObject(
                    ecsBlueGreenRollbackRequest.getOldServiceCreateRequestBuilderString(),
                    CreateServiceRequest.serializableBuilderClass()).build();

            // update desired count of old service to its earlier desired count
            Service service = ecsCommandTaskHelper.updateDesiredCount(ecsBlueGreenRollbackRequest.getOldServiceName(), ecsInfraConfig,
                    awsInternalConfig, createServiceRequest.desiredCount()).service();

            List<ServiceEvent> eventsAlreadyProcessed = new ArrayList<>(service.events());

            ecsCommandTaskHelper.waitForTasksToBeInRunningState(awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO()),
                    ecsInfraConfig.getCluster(), service.serviceName(), ecsInfraConfig.getRegion(),
                    eventsAlreadyProcessed, rollbackLog, timeoutInMillis);

            // steady state check to reach stable state
            ecsCommandTaskHelper.ecsServiceSteadyStateCheck(rollbackLog, ecsInfraConfig.getAwsConnectorDTO(),
                    ecsBlueGreenRollbackRequest.getEcsInfraConfig().getCluster(), createServiceRequest.serviceName(),
                    ecsBlueGreenRollbackRequest.getEcsInfraConfig().getRegion(), timeoutInMillis,
                    eventsAlreadyProcessed);

            //swap target group with correct listener rule arns
            ecsCommandTaskHelper.swapTargetGroups(ecsInfraConfig, rollbackLog,
                    ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig(), awsInternalConfig);

            // update service tag of old service with blue version
            ecsCommandTaskHelper.updateTag(ecsBlueGreenRollbackRequest.getOldServiceName(), ecsInfraConfig,
                    EcsCommandTaskNGHelper.BG_BLUE, awsInternalConfig);

            // update service tag of new service with green version
            ecsCommandTaskHelper.updateTag(ecsBlueGreenRollbackRequest.getNewServiceName(), ecsInfraConfig,
                    EcsCommandTaskNGHelper.BG_GREEN, awsInternalConfig);

            // downsize new service desired count to zero
            ecsCommandTaskHelper.updateDesiredCount(ecsBlueGreenRollbackRequest.getNewServiceName(),
                    ecsInfraConfig, awsInternalConfig, 0);

        }
    }
}
