/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.connectiontask;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DecryptableEntity;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.cvconnector.CVConnectorTaskParams;
import io.harness.delegate.beans.connector.cvconnector.CVConnectorTaskResponse;
import io.harness.delegate.beans.cvng.ConnectorValidationInfo;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class CVNGConnectorValidationDelegateTask extends AbstractDelegateRunnableTask {
  @Inject private DataCollectionDSLService dataCollectionDSLService;
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private Clock clock;
  @Inject private NGErrorHelper ngErrorHelper;

  public CVNGConnectorValidationDelegateTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new IllegalStateException("run should not get called with Object parameters");
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    CVConnectorTaskParams taskParameters = (CVConnectorTaskParams) parameters;
    if (taskParameters.getConnectorConfigDTO() instanceof DecryptableEntity) {
      List<DecryptableEntity> decryptableEntities = taskParameters.getConnectorConfigDTO().getDecryptableEntities();
      List<List<EncryptedDataDetail>> encryptionDetails = taskParameters.getEncryptionDetails();

      if (isEmpty(decryptableEntities)) {
        decryptableEntities = new ArrayList<>();
      }

      if (isEmpty(encryptionDetails)) {
        encryptionDetails = new ArrayList<>();
      }

      for (int decryptableEntityIndex = 0; decryptableEntityIndex < decryptableEntities.size();
           decryptableEntityIndex++) {
        DecryptableEntity decryptableEntity = decryptableEntities.get(decryptableEntityIndex);
        List<EncryptedDataDetail> encryptedDataDetail = encryptionDetails.get(decryptableEntityIndex);
        secretDecryptionService.decrypt(decryptableEntity, encryptedDataDetail);
      }
    }
    boolean validCredentials = false;

    ConnectorValidationInfo connectorValidationInfo =
        ConnectorValidationInfo.getConnectorValidationInfo(taskParameters.getConnectorConfigDTO());
    String dsl = connectorValidationInfo.getConnectionValidationDSL();
    Instant now = clock.instant();
    final RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                                    .baseUrl(connectorValidationInfo.getBaseUrl())
                                                    .commonHeaders(connectorValidationInfo.collectionHeaders())
                                                    .commonOptions(connectorValidationInfo.collectionParams())
                                                    .otherEnvVariables(connectorValidationInfo.getDslEnvVariables())
                                                    .endTime(connectorValidationInfo.getEndTime(now))
                                                    .startTime(connectorValidationInfo.getStartTime(now))
                                                    .build();
    validCredentials = ((String) dataCollectionDSLService.execute(dsl, runtimeParameters)).equalsIgnoreCase("true");
    log.info("connectorValidationInfo {}", connectorValidationInfo);

    return CVConnectorTaskResponse.builder()
        .valid(validCredentials)
        .delegateMetaInfo(DelegateMetaInfo.builder().id(getDelegateId()).build())
        .build();
  }
}
