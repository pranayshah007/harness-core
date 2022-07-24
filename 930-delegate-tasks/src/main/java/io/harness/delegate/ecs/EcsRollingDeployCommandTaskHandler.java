package io.harness.delegate.ecs;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsInfraConfigHelper;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.request.EcsRollingDeployRequest;
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
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.CreateServiceResponse;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;
import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import java.nio.charset.StandardCharsets;
import static java.lang.String.format;
import static software.wings.beans.LogHelper.color;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsRollingDeployCommandTaskHandler extends EcsCommandTaskNGHandler {
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;
  @Inject private EcsInfraConfigHelper ecsInfraConfigHelper;
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;
  private EcsInfraConfig ecsInfraConfig;
  private long timeoutInMillis;


  @Override
  protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
                                                   ILogStreamingTaskClient iLogStreamingTaskClient,
                                                   CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(ecsCommandRequest instanceof EcsRollingDeployRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("ecsCommandRequest", "Must be instance of EcsRollingDeployRequest"));
    }
    EcsRollingDeployRequest ecsRollingDeployRequest = (EcsRollingDeployRequest) ecsCommandRequest;

    timeoutInMillis = ecsRollingDeployRequest.getTimeoutIntervalInMin() * 60000;
    ecsInfraConfig = ecsRollingDeployRequest.getEcsInfraConfig();

    LogCallback deployLogCallback = ecsTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, EcsCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);
    try {
      String ecsTaskDefinitionManifestContent = ecsRollingDeployRequest.getEcsTaskDefinitionManifestContent();
      String ecsServiceDefinitionManifestContent = ecsRollingDeployRequest.getEcsServiceDefinitionManifestContent();

      ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

      RegisterTaskDefinitionRequest.Builder registerTaskDefinitionRequestBuilder = objectMapper.readValue(ecsTaskDefinitionManifestContent.getBytes(StandardCharsets.UTF_8), RegisterTaskDefinitionRequest.serializableBuilderClass());
      RegisterTaskDefinitionRequest registerTaskDefinitionRequest = registerTaskDefinitionRequestBuilder.build();
      CreateServiceRequest.Builder createServiceRequestBuilder = objectMapper.readValue(ecsServiceDefinitionManifestContent.getBytes(StandardCharsets.UTF_8), CreateServiceRequest.serializableBuilderClass());

      deployLogCallback.saveExecutionLog(format("Creating Task Definition with family %s %n", registerTaskDefinitionRequest.family()), LogLevel.INFO);

      RegisterTaskDefinitionResponse registerTaskDefinitionResponse = ecsCommandTaskHelper.createTaskDefinition(registerTaskDefinitionRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
      TaskDefinition taskDefinition = registerTaskDefinitionResponse.taskDefinition();
      String taskDefinitionName = taskDefinition.family() + ":" + taskDefinition.revision();
      String taskDefinitionArn = taskDefinition.taskDefinitionArn();

      deployLogCallback.saveExecutionLog(format("Created Task Definition %s with Arn %s..%n", taskDefinitionName, taskDefinitionArn), LogLevel.INFO);

      // replace cluster and task definition
      CreateServiceRequest createServiceRequest = createServiceRequestBuilder.cluster(ecsInfraConfig.getCluster()).taskDefinition(registerTaskDefinitionResponse.taskDefinition().taskDefinitionArn()).build();

      // if service exists create service, otherwise update service
      boolean serviceExists = ecsCommandTaskHelper.serviceExists(createServiceRequest.cluster(), createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

      if (!serviceExists) {
        deployLogCallback.saveExecutionLog(format("Creating Service with name %s %n", createServiceRequest.serviceName()), LogLevel.INFO);
        CreateServiceResponse createServiceResponse = ecsCommandTaskHelper.createService(createServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
        deployLogCallback.saveExecutionLog(format("Created Service %s with Arn %s %n", createServiceRequest.serviceName(), createServiceResponse.service().serviceArn()), LogLevel.INFO);
      } else {
        UpdateServiceRequest updateServiceRequest = ecsCommandTaskHelper.convertCreateServiceRequestToUpdateServiceRequest(createServiceRequest);
        deployLogCallback.saveExecutionLog(format("Updating Service with name %s %n", updateServiceRequest.service()), LogLevel.INFO);
        UpdateServiceResponse updateServiceResponse = ecsCommandTaskHelper.updateService(updateServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
        deployLogCallback.saveExecutionLog(format("Updated Service %s with Arn %s %n", updateServiceRequest.service(), updateServiceResponse.service().serviceArn()), LogLevel.INFO);
      }
      deployLogCallback.saveExecutionLog(color(format("%n Deployment Successful."), LogColor.Green, LogWeight.Bold),
              LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      return EcsRollingDeployResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    } catch (Exception ex) {
      deployLogCallback.saveExecutionLog(color(format("%n Deployment Failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new EcsNGException(ex);
    }
  }
}
