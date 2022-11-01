/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.trigger;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;

import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class WebHookTriggerTask extends AbstractDelegateRunnableTask {
  private static final String HMAC_SHA_256 = "HmacSHA256";

  @Inject private EncryptionService encryptionService;

  public WebHookTriggerTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public WebHookTriggerResponseData run(Object[] parameters) {
    return executeInternal((WebhookTriggerParameters) parameters[0]);
  }

  @Override
  public WebHookTriggerResponseData run(TaskParameters parameters) {
    return executeInternal((WebhookTriggerParameters) parameters);
  }

  private WebHookTriggerResponseData executeInternal(WebhookTriggerParameters webhookTriggerParameters) {
    String secretKey;
    try {
      secretKey =
          new String(encryptionService.getDecryptedValue(webhookTriggerParameters.getEncryptedDataDetail(), false));
      if (isEmpty(secretKey)) {
        return getFailureResponseData("Secret key not found in secret manager");
      }
    } catch (RuntimeException ex) {
      log.error("Error finding secret key in secret manager", ex);
      return getFailureResponseData("Error finding secret key in secret manager");
    }

    WebhookSource webhookSource = webhookTriggerParameters.getWebhookSource();
    if (WebhookSource.GITHUB.equals(webhookSource)) {
      return authenticateGithubWebhook(webhookTriggerParameters, secretKey);
    }
    return getFailureResponseData("Webhook authentication is not implemented for " + webhookSource);
  }

  private WebHookTriggerResponseData getFailureResponseData(String errorMessage) {
    return WebHookTriggerResponseData.builder()
        .executionStatus(ExecutionStatus.FAILED)
        .errorMessage(errorMessage)
        .build();
  }

  private WebHookTriggerResponseData authenticateGithubWebhook(
      WebhookTriggerParameters webhookTriggerParameters, String secretKey) {
    try {
      String hashedPayloadUsingHarnessSecret =
          "sha256=" + encode(secretKey, webhookTriggerParameters.getEventPayload());
      if (hashedPayloadUsingHarnessSecret.equals(webhookTriggerParameters.getHashedPayload())) {
        return getSuccessfulResponseData(true);
      }
      return getSuccessfulResponseData(false);
    } catch (Exception ex) {
      log.info("Error encoding the payload", ex);
      return getFailureResponseData("Error encoding the payload" + ex.getMessage());
    }
  }

  private WebHookTriggerResponseData getSuccessfulResponseData(boolean isWebhookAuthenticated) {
    return WebHookTriggerResponseData.builder()
        .executionStatus(ExecutionStatus.SUCCESS)
        .isWebhookAuthenticated(isWebhookAuthenticated)
        .build();
  }

  private String encode(String key, String data) throws Exception {
    Mac mac = Mac.getInstance(HMAC_SHA_256);
    byte[] secretKey = key.getBytes("UTF-8");
    mac.init(new SecretKeySpec(secretKey, HMAC_SHA_256));
    byte[] encodedData = mac.doFinal(data.getBytes("UTF-8"));
    return Hex.encodeHexString(encodedData);
  }
}
