package io.harness.delegate.task.artifacts.azuremachineimage;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.eraro.ErrorCode;
import io.harness.exception.runtime.GcpClientRuntimeException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class AzureMachineImageTaskHelper {
  private final AzureMachineImageTaskHandler azureMachineImageTaskHandler;
  public ArtifactTaskResponse getArtifactCollectResponse(ArtifactTaskParameters artifactTaskParameters) {
    return getArtifactCollectResponse(artifactTaskParameters, null);
  }
  public ArtifactTaskResponse getArtifactCollectResponse(
      ArtifactTaskParameters artifactTaskParameters, LogCallback executionLogCallback) {
    AzureMachineImageDelegateRequest attributes =
        (AzureMachineImageDelegateRequest) artifactTaskParameters.getAttributes();
    azureMachineImageTaskHandler.decryptRequestDTOs(attributes);
    ArtifactTaskResponse artifactTaskResponse;
    try {
      switch (artifactTaskParameters.getArtifactTaskType()) {
        case GET_RESOURCE_GROUPS:
          saveLogs(executionLogCallback, "Fetching Artifact details");
          artifactTaskResponse = getSuccessTaskResponse(azureMachineImageTaskHandler.getResourceGroups(attributes));
          break;
        default:
          saveLogs(executionLogCallback,
              "No corresponding Azure Machine Image task type [{}]: " + artifactTaskParameters.toString());
          log.error("No corresponding Azure Machine Image artifact task type [{}]", artifactTaskParameters.toString());
          return ArtifactTaskResponse.builder()
              .commandExecutionStatus(CommandExecutionStatus.FAILURE)
              .errorMessage("There is no Azure Machine Image artifact task type impl defined for - "
                  + artifactTaskParameters.getArtifactTaskType().name())
              .errorCode(ErrorCode.INVALID_ARGUMENT)
              .build();
      }
    } catch (GcpClientRuntimeException ex) {
      //            if (GlobalContextManager.get(MdcGlobalContextData.MDC_ID) == null) {
      //                MdcGlobalContextData mdcGlobalContextData = MdcGlobalContextData.builder().map(new
      //                HashMap<>()).build(); GlobalContextManager.upsertGlobalContextRecord(mdcGlobalContextData);
      //            }
      //            ((MdcGlobalContextData) GlobalContextManager.get(MdcGlobalContextData.MDC_ID))
      //                    .getMap()
      //                    .put(ExceptionMetadataKeys.CONNECTOR.name(), attributes.getConnectorRef());
      throw ex;
    }
    return artifactTaskResponse;
  }
  private ArtifactTaskResponse getSuccessTaskResponse(ArtifactTaskExecutionResponse taskExecutionResponse) {
    return ArtifactTaskResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .artifactTaskExecutionResponse(taskExecutionResponse)
        .build();
  }
  private void saveLogs(LogCallback executionLogCallback, String message) {
    if (executionLogCallback != null) {
      executionLogCallback.saveExecutionLog(message);
    }
  }
}
