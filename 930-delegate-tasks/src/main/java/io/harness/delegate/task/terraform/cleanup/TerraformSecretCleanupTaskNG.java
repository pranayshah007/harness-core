/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.terraform.cleanup;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.TerraformSecretCleanupFailureException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.secretmanagerclient.EncryptDecryptHelper;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.lang.JoseException;

@Slf4j
@OwnedBy(CDP)
public class TerraformSecretCleanupTaskNG extends AbstractDelegateRunnableTask {
  private static final String TF_CLEANUP_FAILURE = "Failed to clean Vault terraform secret for cleanup Uuid: %s";
  @Inject EncryptDecryptHelper encryptDecryptHelper;

  public TerraformSecretCleanupTaskNG(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    if (!(parameters instanceof TerraformSecretCleanupTaskParameters)) {
      throw new UnsupportedOperationException("Unsupported parameters type");
    }

    TerraformSecretCleanupTaskParameters terraformCleanupParams = (TerraformSecretCleanupTaskParameters) parameters;

    if (terraformCleanupParams.getEncryptionConfig() == null) {
      throw new TerraformSecretCleanupFailureException(
          "EncryptionConfig should not be null when deleting terraform vault secret");
    }

    try {
      log.info(String.format(
          "Cleaning up terraform plan from vault for cleanup Uuid: %s", terraformCleanupParams.getCleanupUuid()));
      List<TerraformSecretCleanupFailureDetails> encryptedPlanWithExceptionMessageList = new ArrayList<>();

      terraformCleanupParams.getEncryptedRecordDataList().forEach(encryptedRecordData -> {
        try {
          boolean isSafelyDeleted = encryptDecryptHelper.deleteEncryptedRecord(
              terraformCleanupParams.getEncryptionConfig(), encryptedRecordData);
          if (isSafelyDeleted) {
            log.info("Terraform Plan has been safely deleted from vault");
          }
        } catch (Exception exception) {
          log.error(String.format(TF_CLEANUP_FAILURE, terraformCleanupParams.getCleanupUuid()));
          String exceptionMessage =
              exception.getCause() != null ? exception.getCause().getMessage() : exception.getMessage();
          encryptedPlanWithExceptionMessageList.add(TerraformSecretCleanupFailureDetails.builder()
                                                        .encryptedRecordData(encryptedRecordData)
                                                        .exceptionMessage(exceptionMessage)
                                                        .build());
        }
      });

      return TerraformSecretCleanupTaskResponse.builder()
          .responseDataUuid(terraformCleanupParams.getCleanupUuid())
          .secretCleanupFailureDetailsList(encryptedPlanWithExceptionMessageList)
          .build();
    } catch (Exception ex) {
      log.error(String.format(TF_CLEANUP_FAILURE, terraformCleanupParams.getCleanupUuid()));
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(ex);
      throw new TerraformSecretCleanupFailureException(
          String.format(TF_CLEANUP_FAILURE, terraformCleanupParams.getCleanupUuid()), sanitizeException.getCause());
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}