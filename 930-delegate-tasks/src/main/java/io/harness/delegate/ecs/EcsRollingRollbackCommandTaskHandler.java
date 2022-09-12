package io.harness.delegate.ecs;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ecs.EcsRollingRollbackResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsInfraConfigHelper;
import io.harness.delegate.task.ecs.EcsRollingRollbackConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.request.EcsRollingRollbackRequest;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.delegate.task.ecs.response.EcsRollingRollbackResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsRollingRollbackCommandTaskHandler extends EcsCommandTaskNGHandler {
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;
  @Inject private EcsInfraConfigHelper ecsInfraConfigHelper;
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;

  private EcsInfraConfig ecsInfraConfig;
  private long timeoutInMillis;

  @Override
  protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(ecsCommandRequest instanceof EcsRollingRollbackRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("ecsCommandRequest", "Must be instance of EcsRollingRollbackRequest"));
    }

    EcsRollingRollbackRequest ecsRollingRollbackRequest = (EcsRollingRollbackRequest) ecsCommandRequest;
    timeoutInMillis = ecsRollingRollbackRequest.getTimeoutIntervalInMin() * 60000;
    ecsInfraConfig = ecsRollingRollbackRequest.getEcsInfraConfig();

    LogCallback rollbackLogCallback = ecsTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, EcsCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);

    try {
      EcsRollingRollbackConfig ecsRollingRollbackConfig = ecsRollingRollbackRequest.getEcsRollingRollbackConfig();

      boolean isFirstDeployment = ecsRollingRollbackConfig.isFirstDeployment();

      EcsRollingRollbackResponse ecsRollingRollbackResponse = null;

      if (!isFirstDeployment) {
        CreateServiceRequest.Builder createServiceRequestBuilder =
            ecsCommandTaskHelper.parseYamlAsObject(ecsRollingRollbackConfig.getCreateServiceRequestBuilderString(),
                CreateServiceRequest.serializableBuilderClass());
        // replace cluster
        CreateServiceRequest createServiceRequest =
            createServiceRequestBuilder.cluster(ecsInfraConfig.getCluster()).build();

        rollback(createServiceRequest, ecsRollingRollbackConfig.getRegisterScalableTargetRequestBuilderStrings(),
            ecsRollingRollbackConfig.getRegisterScalingPolicyRequestBuilderStrings(), ecsInfraConfig,
            rollbackLogCallback, timeoutInMillis);

        EcsRollingRollbackResult ecsRollingRollbackResult =
            EcsRollingRollbackResult.builder()
                .firstDeployment(isFirstDeployment)
                .region(ecsInfraConfig.getRegion())
                .ecsTasks(ecsCommandTaskHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(),
                    ecsInfraConfig.getCluster(), createServiceRequest.serviceName(), ecsInfraConfig.getRegion()))
                .infrastructureKey(ecsInfraConfig.getInfraStructureKey())
                .build();

        ecsRollingRollbackResponse = EcsRollingRollbackResponse.builder()
                                         .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                         .ecsRollingRollbackResult(ecsRollingRollbackResult)
                                         .build();

      } else {
        String serviceName = ecsRollingRollbackConfig.getServiceName();

        rollbackLogCallback.saveExecutionLog(format("Deleting service %s..", serviceName), LogLevel.INFO);

        ecsCommandTaskHelper.deleteService(
            serviceName, ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

        ecsCommandTaskHelper.ecsServiceInactiveStateCheck(rollbackLogCallback, ecsInfraConfig.getAwsConnectorDTO(),
            ecsInfraConfig.getCluster(), serviceName, ecsInfraConfig.getRegion(),
            (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));

        rollbackLogCallback.saveExecutionLog(format("Deleted service %s", serviceName), LogLevel.INFO);

        EcsRollingRollbackResult ecsRollingRollbackResult =
            EcsRollingRollbackResult.builder()
                .firstDeployment(isFirstDeployment)
                .region(ecsInfraConfig.getRegion())
                .infrastructureKey(ecsInfraConfig.getInfraStructureKey())
                .build();

        ecsRollingRollbackResponse = EcsRollingRollbackResponse.builder()
                                         .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                         .ecsRollingRollbackResult(ecsRollingRollbackResult)
                                         .build();
      }

      rollbackLogCallback.saveExecutionLog(color(format("%n Rollback Successful."), LogColor.Green, LogWeight.Bold),
          LogLevel.INFO, CommandExecutionStatus.SUCCESS);

      return ecsRollingRollbackResponse;

    } catch (Exception ex) {
      rollbackLogCallback.saveExecutionLog(color(format("%n Rollback Failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new EcsNGException(ex);
    }
  }

  private void rollback(CreateServiceRequest createServiceRequest, List<String> ecsScalableTargetManifestContentList,
      List<String> ecsScalingPolicyManifestContentList, EcsInfraConfig ecsInfraConfig, LogCallback rollbackLogCallback,
      long timeoutInMillis) {
    ecsCommandTaskHelper.createOrUpdateService(createServiceRequest, ecsScalableTargetManifestContentList,
        ecsScalingPolicyManifestContentList, ecsInfraConfig, rollbackLogCallback, timeoutInMillis, false, false);
  }
}
