package io.harness.delegate.task.pcf;

import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.pcf.PcfValidationHandler;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.pcfconnector.PcfTaskParams;
import io.harness.delegate.beans.connector.pcfconnector.PcfTaskType;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.response.PcfValidateTaskResponse;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

public class PcfConnectorValidationTask extends AbstractDelegateRunnableTask {
  @Inject private PcfValidationHandler pcfValidationHandler;

  public PcfConnectorValidationTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    final PcfTaskParams pcfTaskParams = (PcfTaskParams) parameters;
    final PcfTaskType pcfTaskType = pcfTaskParams.getPcfTaskType();
    if (Objects.isNull(pcfTaskType)) {
      throw new InvalidRequestException("Task type not provided");
    }

    final List<EncryptedDataDetail> encryptionDetails = pcfTaskParams.getEncryptionDetails();
    if (pcfTaskType == PcfTaskType.VALIDATE) {
      return handleValidateTask(pcfTaskParams, encryptionDetails);
    } else {
      throw new InvalidRequestException("Task type not identified");
    }
  }

  public DelegateResponseData handleValidateTask(
      PcfTaskParams pcfTaskParams, List<EncryptedDataDetail> encryptionDetails) {
    ConnectorValidationResult connectorValidationResult =
        pcfValidationHandler.validate(pcfTaskParams, encryptionDetails);
    connectorValidationResult.setDelegateId(getDelegateId());
    return PcfValidateTaskResponse.builder().connectorValidationResult(connectorValidationResult).build();
  }
}
