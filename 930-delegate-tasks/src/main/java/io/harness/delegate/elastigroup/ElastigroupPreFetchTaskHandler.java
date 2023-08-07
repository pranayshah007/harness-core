/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.elastigroup;

import static software.wings.beans.LogHelper.color;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.delegate.beans.elastigroup.ElastigroupPreFetchResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.ElastigroupNGException;
import io.harness.delegate.task.elastigroup.ElastigroupCommandTaskNGHelper;
import io.harness.delegate.task.elastigroup.request.ElastigroupCommandRequest;
import io.harness.delegate.task.elastigroup.request.ElastigroupPreFetchRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupCommandResponse;
import io.harness.delegate.task.elastigroup.response.ElastigroupPreFetchResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.elastigroup.ElastigroupCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroup;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class ElastigroupPreFetchTaskHandler extends ElastigroupCommandTaskHandler {
  @Inject private ElastigroupCommandTaskNGHelper elastigroupCommandTaskNGHelper;
  @Inject protected SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;
  private long timeoutInMillis;

  @Override
  protected ElastigroupCommandResponse executeTaskInternal(ElastigroupCommandRequest elastigroupCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws ElastigroupNGException {
    if (!(elastigroupCommandRequest instanceof ElastigroupPreFetchRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("elastigroupCommandRequest", "Must be instance of ElastigroupPreFetchRequest"));
    }
    ElastigroupPreFetchRequest elastigroupPreFetchRequest = (ElastigroupPreFetchRequest) elastigroupCommandRequest;

    String elastigroupNamePrefix = elastigroupPreFetchRequest.getElastigroupNamePrefix();
    boolean blueGreen = elastigroupPreFetchRequest.isBlueGreen();

    timeoutInMillis = elastigroupPreFetchRequest.getTimeoutIntervalInMin() * 60000;

    LogCallback deployLogCallback = elastigroupCommandTaskNGHelper.getLogCallback(iLogStreamingTaskClient,
        ElastigroupCommandUnitConstants.CREATE_ELASTIGROUP.toString(), true, commandUnitsProgress);
    deployLogCallback.saveExecutionLog("Creating snapshot of Elastigroups");

    try {
      SpotInstConfig spotInstConfig = elastigroupPreFetchRequest.getSpotInstConfig();
      elastigroupCommandTaskNGHelper.decryptSpotInstConfig(spotInstConfig);
      SpotPermanentTokenConfigSpecDTO spotPermanentTokenConfigSpecDTO =
          (SpotPermanentTokenConfigSpecDTO) spotInstConfig.getSpotConnectorDTO().getCredential().getConfig();
      String spotInstAccountId = spotPermanentTokenConfigSpecDTO.getSpotAccountIdRef().getDecryptedValue() != null
          ? String.valueOf(spotPermanentTokenConfigSpecDTO.getSpotAccountIdRef().getDecryptedValue())
          : spotPermanentTokenConfigSpecDTO.getSpotAccountId();
      String spotInstApiToken = String.valueOf(spotPermanentTokenConfigSpecDTO.getApiTokenRef().getDecryptedValue());

      List<ElastiGroup> elastigroups = elastigroupCommandTaskNGHelper.listElastigroups(
          blueGreen, elastigroupPreFetchRequest.getElastigroupNamePrefix(), spotInstAccountId, spotInstApiToken);

      ElastigroupPreFetchResult result = ElastigroupPreFetchResult.builder().elastigroups(elastigroups).build();

      return ElastigroupPreFetchResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .elastigroupPreFetchResult(result)
          .build();

    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      deployLogCallback.saveExecutionLog(sanitizedException.getMessage());
      deployLogCallback.saveExecutionLog(
          color("Deployment Failed.", LogColor.Red, LogWeight.Bold), LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      return ElastigroupPreFetchResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(sanitizedException.getMessage())
          .build();
    }
  }
}
