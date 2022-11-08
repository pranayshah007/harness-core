package io.harness.connector.task.pcf;

import static io.harness.exception.WingsException.ExecutionContext.MANAGER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.pcfconnector.PcfConnectorDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfTaskParams;
import io.harness.delegate.beans.connector.pcfconnector.PcfValidationParams;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.exception.ngexception.ConnectorValidationException;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.PcfConfig;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class PcfValidationHandler implements ConnectorValidationHandler {
  @Inject protected CfDeploymentManager pcfDeploymentManager;
  @Inject private PcfNgConfigMapper ngConfigMapper;
  @Inject ExceptionManager exceptionManager;
  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    try {
      final PcfValidationParams pcfValidationParams = (PcfValidationParams) connectorValidationParams;
      final PcfConnectorDTO pcfConnectorDTO = pcfValidationParams.getPcfConnector();
      final List<EncryptedDataDetail> encryptedDataDetails = pcfValidationParams.getEncryptionDetails();
      return validateInternal(pcfConnectorDTO, encryptedDataDetails);
    } catch (Exception e) {
      throw exceptionManager.processException(e, MANAGER, log);
    }
  }

  public ConnectorValidationResult validate(PcfTaskParams pcfTaskParams, List<EncryptedDataDetail> encryptionDetails) {
    final PcfConnectorDTO pcfConnectorDTO = pcfTaskParams.getPcfConnector();
    return validateInternal(pcfConnectorDTO, encryptionDetails);
  }

  private ConnectorValidationResult validateInternal(
      PcfConnectorDTO pcfConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    PcfConfig pcfConfig = ngConfigMapper.mapPcfConfigWithDecryption(pcfConnectorDTO, encryptedDataDetails);
    return handleValidateTask(pcfConfig);
  }

  private ConnectorValidationResult handleValidateTask(PcfConfig pcfConfig) {
    try {
      // todo: ask about limit pcf threads FF
      pcfDeploymentManager.getOrganizations(CfRequestConfig.builder()
                                                .userName(String.valueOf(pcfConfig.getUserName()))
                                                .password(String.valueOf(pcfConfig.getPasswordRef()))
                                                .endpointUrl(pcfConfig.getEndpointUrl())
                                                .timeOutIntervalInMins(2)
                                                .build());
      return ConnectorValidationResult.builder()
          .status(ConnectivityStatus.SUCCESS)
          .testedAt(System.currentTimeMillis())
          .build();
    } catch (Exception e) {
      String errorMessage = "Testing connection to Pcf has Failed.";
      throw NestedExceptionUtils.hintWithExplanationException("Failed to validate connection for Pcf connector",
          "Please check you Pcf connector configuration.", new ConnectorValidationException(errorMessage));
    }
  }
}
