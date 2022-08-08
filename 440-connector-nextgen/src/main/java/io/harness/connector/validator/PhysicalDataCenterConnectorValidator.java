/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.pdcconnector.HostDTO;
import io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorDTO;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.ng.validator.dto.HostValidationDTO;
import io.harness.ng.validator.service.api.NGHostValidationService;

import com.google.inject.Inject;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDP)
public class PhysicalDataCenterConnectorValidator implements ConnectionValidator<PhysicalDataCenterConnectorDTO> {
  @Inject private NGHostValidationService hostValidationService;

  @Override
  public ConnectorValidationResult validate(PhysicalDataCenterConnectorDTO connectorDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    long startTestingAt = System.currentTimeMillis();
    List<HostValidationDTO> hostValidationDTOs =
        hostValidationService.validateHostsConnectivity(getHostNames(connectorDTO), accountIdentifier, orgIdentifier,
            projectIdentifier, connectorDTO.getDelegateSelectors());

    return buildConnectorValidationResult(hostValidationDTOs, startTestingAt);
  }

  @Override
  public ConnectorValidationResult validate(ConnectorResponseDTO connectorResponseDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    return null;
  }

  @NotNull
  private List<String> getHostNames(PhysicalDataCenterConnectorDTO connectorDTO) {
    return connectorDTO.getHosts().stream().map(HostDTO::getHostName).collect(Collectors.toList());
  }

  private ConnectorValidationResult buildConnectorValidationResult(
      List<HostValidationDTO> hostValidationDTOs, long startTestingAt) {
    ConnectivityStatus connectivityStatus = hostValidationDTOs.stream().anyMatch(isHostValidationStatusFailed())
        ? ConnectivityStatus.FAILURE
        : ConnectivityStatus.SUCCESS;

    return connectivityStatus == ConnectivityStatus.SUCCESS ? buildConnectorValidationResultSuccess(startTestingAt)
                                                            : buildConnectorValidationResultFailure(hostValidationDTOs);
  }

  private ConnectorValidationResult buildConnectorValidationResultSuccess(long startTestingAt) {
    return ConnectorValidationResult.builder().testedAt(startTestingAt).status(ConnectivityStatus.SUCCESS).build();
  }

  private ConnectorValidationResult buildConnectorValidationResultFailure(List<HostValidationDTO> hostValidationDTOS) {
    String detailedErrorMsg =
        format("Socket connectivity checks failed for host(s) %n%s", getFailedHostsList(hostValidationDTOS));

    String passedHostList = getPassedHostList(hostValidationDTOS);
    if (EmptyPredicate.isNotEmpty(passedHostList)) {
      detailedErrorMsg =
          format("%s%n%n Socket connectivity checks passed for host(s) %n%s", detailedErrorMsg, passedHostList);
    }

    throw NestedExceptionUtils.hintWithExplanationException(HintException.HINT_SOCKET_CONNECTION_TO_HOST_UNREACHABLE,
        ExplanationException.DELEGATE_TO_HOST_SOCKET_CONNECTION_FAILED,
        new InvalidRequestException(detailedErrorMsg, WingsException.USER));
  }

  private String getFailedHostsList(@NotNull List<HostValidationDTO> hostValidationDTOS) {
    return hostValidationDTOS.stream()
        .filter(isHostValidationStatusFailed())
        .map(hostValidationDTO
            -> format(
                "[host]: %s, [message]: %s", hostValidationDTO.getHost(), hostValidationDTO.getError().getMessage()))
        .collect(Collectors.joining("\n"));
  }

  private String getPassedHostList(@NotNull List<HostValidationDTO> hostValidationDTOS) {
    List<String> validationPassedHostNames = hostValidationDTOS.stream()
                                                 .filter(isHostValidationStatusSuccess())
                                                 .map(HostValidationDTO::getHost)
                                                 .collect(Collectors.toList());
    return StringUtils.join(validationPassedHostNames, '\n');
  }

  @NotNull
  private Predicate<HostValidationDTO> isHostValidationStatusFailed() {
    return hostValidationDTO -> hostValidationDTO.getStatus() == HostValidationDTO.HostValidationStatus.FAILED;
  }

  @NotNull
  private Predicate<HostValidationDTO> isHostValidationStatusSuccess() {
    return hostValidationDTO -> hostValidationDTO.getStatus() == HostValidationDTO.HostValidationStatus.SUCCESS;
  }
}
