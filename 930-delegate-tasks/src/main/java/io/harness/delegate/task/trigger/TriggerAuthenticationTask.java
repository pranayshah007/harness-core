/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.trigger;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.beans.WebhookEncryptedSecretDTO;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.gitapi.GitRepoType;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.trigger.TriggerAuthenticationTaskParams;
import io.harness.delegate.beans.trigger.TriggerAuthenticationTaskResponse;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ngtriggers.WebhookSecretData;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
public class TriggerAuthenticationTask extends AbstractDelegateRunnableTask {
  private static final String HMAC_SHA_256 = "HmacSHA256";
  @Inject private SecretDecryptionService decryptionService;

  public TriggerAuthenticationTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    TriggerAuthenticationTaskParams taskParams = (TriggerAuthenticationTaskParams) parameters;
    GitRepoType gitRepoType = taskParams.getGitRepoType();
    if (gitRepoType != GitRepoType.GITHUB) {
      throw new UnsupportedOperationException("Only GitHub trigger authentication is supported");
    }
    List<Boolean> triggersAuthenticationStatus = new ArrayList<>();
    for (WebhookSecretData webhookSecretData : taskParams.getWebhookSecretData()) {
      WebhookEncryptedSecretDTO decryptableEntity = webhookSecretData.getWebhookEncryptedSecretDTO();
      List<EncryptedDataDetail> encryptedDataDetail = webhookSecretData.getEncryptedDataDetails();
      Boolean authenticationStatus = false;
      String secret = null;
      try {
        decryptionService.decrypt(decryptableEntity, encryptedDataDetail);
        secret = String.valueOf(decryptableEntity.getSecretRef().getDecryptedValue());
      } catch (RuntimeException ex) {
        log.error("Error finding secret key in secret manager", ex);
      }
      if (!isEmpty(secret)) {
        authenticationStatus = authenticateTrigger(taskParams.getEventPayload(), taskParams.getHashedPayload(), secret);
      } else {
        log.warn("Decrypted secret for trigger authentication task is empty");
      }
      triggersAuthenticationStatus.add(authenticationStatus);
    }
    return getSuccessfulResponseData(triggersAuthenticationStatus);
  }

  private Boolean authenticateTrigger(String payload, String hashedPayload, String secret) {
    try {
      String hashedPayloadUsingSecret = "sha256=" + encode(secret, payload);
      if (hashedPayloadUsingSecret.equals(hashedPayload)) {
        log.info("Trigger authentication success");
        return true;
      }
      log.warn("hashedPayload does not match hashedPayloadUsingSecret: hashedPayload: [" + hashedPayload
          + "], hashedPayloadUsingSecret: [" + hashedPayloadUsingSecret + "]");
      return false;
    } catch (Exception ex) {
      log.error("Error encoding the payload", ex);
      return false;
    }
  }

  private String encode(String key, String data) throws Exception {
    Mac mac = Mac.getInstance(HMAC_SHA_256);
    byte[] secretKey = key.getBytes("UTF-8");
    mac.init(new SecretKeySpec(secretKey, HMAC_SHA_256));
    byte[] encodedData = mac.doFinal(data.getBytes("UTF-8"));
    return Hex.encodeHexString(encodedData);
  }

  private TriggerAuthenticationTaskResponse getSuccessfulResponseData(List<Boolean> triggersAuthenticationStatus) {
    return TriggerAuthenticationTaskResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .triggersAuthenticationStatus(triggersAuthenticationStatus)
        .build();
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }
}
