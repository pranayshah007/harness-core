package software.wings.delegatetasks.pcf.pcftaskhandler;

import static io.harness.pcf.model.PcfConstants.PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX;
import static java.util.stream.Collectors.toList;
import static software.wings.beans.Log.LogColor.White;
import static software.wings.beans.Log.LogWeight.Bold;
import static software.wings.beans.Log.color;

import com.google.inject.Singleton;

import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.command.CommandExecutionResult.CommandExecutionStatus;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.security.encryption.EncryptedDataDetail;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import software.wings.api.PcfInstanceElement;
import software.wings.api.pcf.PcfServiceData;
import software.wings.beans.PcfConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.helpers.ext.pcf.PcfRequestConfig;
import software.wings.helpers.ext.pcf.PivotalClientApiException;
import software.wings.helpers.ext.pcf.request.PcfCommandRequest;
import software.wings.helpers.ext.pcf.request.PcfCommandRollbackRequest;
import software.wings.helpers.ext.pcf.response.PcfAppSetupTimeDetails;
import software.wings.helpers.ext.pcf.response.PcfCommandExecutionResponse;
import software.wings.helpers.ext.pcf.response.PcfDeployCommandResponse;
import software.wings.utils.Misc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@NoArgsConstructor
@Singleton
@Slf4j
public class PcfRollbackCommandTaskHandler extends PcfCommandTaskHandler {
  /**
   * This method performs Rollback operation
   * @param pcfCommandRequest
   * @param encryptedDataDetails
   * @return
   */
  public PcfCommandExecutionResponse executeTaskInternal(PcfCommandRequest pcfCommandRequest,
      List<EncryptedDataDetail> encryptedDataDetails, ExecutionLogCallback executionLogCallback) {
    if (!(pcfCommandRequest instanceof PcfCommandRollbackRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("pcfCommandRequest", "Must be instance of PcfCommandRollbackRequest"));
    }
    executionLogCallback.saveExecutionLog(color("--------- Starting Rollback deployment", White, Bold));
    List<PcfServiceData> pcfServiceDataUpdated = new ArrayList<>();
    PcfDeployCommandResponse pcfDeployCommandResponse =
        PcfDeployCommandResponse.builder().pcfInstanceElements(new ArrayList<>()).build();

    PcfCommandRollbackRequest commandRollbackRequest = (PcfCommandRollbackRequest) pcfCommandRequest;

    try {
      PcfConfig pcfConfig = pcfCommandRequest.getPcfConfig();
      encryptionService.decrypt(pcfConfig, encryptedDataDetails);
      if (CollectionUtils.isEmpty(commandRollbackRequest.getInstanceData())) {
        commandRollbackRequest.setInstanceData(new ArrayList<>());
      }

      PcfRequestConfig pcfRequestConfig = PcfRequestConfig.builder()
                                              .userName(pcfConfig.getUsername())
                                              .password(String.valueOf(pcfConfig.getPassword()))
                                              .endpointUrl(pcfConfig.getEndpointUrl())
                                              .orgName(commandRollbackRequest.getOrganization())
                                              .spaceName(commandRollbackRequest.getSpace())
                                              .timeOutIntervalInMins(commandRollbackRequest.getTimeoutIntervalInMin())
                                              .build();

      // get Upsize Instance data
      List<PcfServiceData> upsizeList =
          commandRollbackRequest.getInstanceData()
              .stream()
              .filter(pcfServiceData -> pcfServiceData.getDesiredCount() > pcfServiceData.getPreviousCount())
              .collect(toList());

      // get Downsize Instance data
      List<PcfServiceData> downSizeList =
          commandRollbackRequest.getInstanceData()
              .stream()
              .filter(pcfServiceData -> pcfServiceData.getDesiredCount() < pcfServiceData.getPreviousCount())
              .collect(toList());

      List<PcfInstanceElement> pcfInstanceElements = new ArrayList<>();
      // During rollback, always upsize old ones
      pcfCommandTaskHelper.upsizeListOfInstances(executionLogCallback, pcfDeploymentManager, pcfServiceDataUpdated,
          pcfRequestConfig, upsizeList, pcfInstanceElements);
      pcfCommandTaskHelper.downSizeListOfInstances(
          executionLogCallback, pcfDeploymentManager, pcfServiceDataUpdated, pcfRequestConfig, downSizeList);

      // This steps is only required for Simulated BG workflow
      if (isRollbackRoutesRequired(pcfRequestConfig, commandRollbackRequest)) {
        // Remove any routes attached during routemap phase
        pcfCommandTaskHelper.unmapExistingRouteMaps(
            commandRollbackRequest.getNewApplicationDetails().getApplicationName(), pcfRequestConfig,
            executionLogCallback);
        // Associate original routes attached during app create.
        pcfCommandTaskHelper.mapRouteMaps(commandRollbackRequest.getNewApplicationDetails().getApplicationName(),
            commandRollbackRequest.getNewApplicationDetails().getUrls(), pcfRequestConfig, executionLogCallback);

        // Perform same activity for existing apps, those were updated with route maps.
        if (EmptyPredicate.isNotEmpty(commandRollbackRequest.getAppsToBeDownSized())) {
          for (PcfAppSetupTimeDetails appDetails : commandRollbackRequest.getAppsToBeDownSized()) {
            pcfCommandTaskHelper.unmapExistingRouteMaps(
                appDetails.getApplicationName(), pcfRequestConfig, executionLogCallback);
            pcfCommandTaskHelper.mapRouteMaps(
                appDetails.getApplicationName(), appDetails.getUrls(), pcfRequestConfig, executionLogCallback);
          }
        }
      }

      pcfDeployCommandResponse.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);
      pcfDeployCommandResponse.setOutput(StringUtils.EMPTY);
      pcfDeployCommandResponse.setInstanceDataUpdated(pcfServiceDataUpdated);
      pcfDeployCommandResponse.getPcfInstanceElements().addAll(pcfInstanceElements);
      executionLogCallback.saveExecutionLog("\n\n--------- PCF Rollback completed successfully");
    } catch (Exception e) {
      logger.error(PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX + "Exception in processing PCF Rollback task [{}]",
          commandRollbackRequest, e);
      executionLogCallback.saveExecutionLog("\n\n--------- PCF Rollback failed to complete successfully");
      Misc.logAllMessages(e, executionLogCallback);
      pcfDeployCommandResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
      pcfDeployCommandResponse.setInstanceDataUpdated(pcfServiceDataUpdated);
      pcfDeployCommandResponse.setOutput(ExceptionUtils.getMessage(e));
    }

    return PcfCommandExecutionResponse.builder()
        .commandExecutionStatus(pcfDeployCommandResponse.getCommandExecutionStatus())
        .errorMessage(pcfDeployCommandResponse.getOutput())
        .pcfCommandResponse(pcfDeployCommandResponse)
        .build();
  }

  private boolean isRollbackRoutesRequired(PcfRequestConfig pcfRequestConfig,
      PcfCommandRollbackRequest pcfCommandRollbackRequest) throws PivotalClientApiException {
    if (pcfCommandRollbackRequest.isStandardBlueGreenWorkflow()) {
      return false;
    }

    pcfRequestConfig.setApplicationName(pcfCommandRollbackRequest.getNewApplicationDetails().getApplicationName());
    ApplicationDetail applicationDetail = pcfDeploymentManager.getApplicationByName(pcfRequestConfig);
    Set<String> urls = new HashSet<>(applicationDetail.getUrls());

    if (urls.containsAll(pcfCommandRollbackRequest.getNewApplicationDetails().getUrls())) {
      return false;
    }

    return true;
  }
}
