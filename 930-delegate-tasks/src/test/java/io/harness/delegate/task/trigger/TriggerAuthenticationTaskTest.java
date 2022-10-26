/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.trigger;

import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.beans.WebhookEncryptedSecretDTO;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.gitapi.GitRepoType;
import io.harness.delegate.beans.trigger.TriggerAuthenticationTaskParams;
import io.harness.delegate.beans.trigger.TriggerAuthenticationTaskResponse;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ngtriggers.WebhookSecretData;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.service.impl.security.SecretDecryptionServiceImpl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class TriggerAuthenticationTaskTest extends CategoryTest {
  private static final String DELEGATE_ID = "DELEGATE_ID";
  private static final String RESOURCE_FILE = "trigger/GitHubSamplePayload.json";
  private static final char[] SECRET = new char[] {'1', '2', '3', '4'};
  private static final String HASHED_PAYLOAD =
      "sha256=d9953c49e0e6fc141185ec53427170b144ecb474bf5aab4721db26faa90c4608";

  SecretDecryptionService decryptionService;
  @InjectMocks
  TriggerAuthenticationTask triggerAuthenticationTask = new TriggerAuthenticationTask(
      DelegateTaskPackage.builder()
          .delegateId(DELEGATE_ID)
          .data(TaskData.builder().async(false).timeout(DEFAULT_SYNC_CALL_TIMEOUT).build())
          .build(),
      null, delegateTaskResponse -> {}, () -> true);

  @Before
  public void setUp() throws Exception {
    decryptionService = spy(new SecretDecryptionServiceImpl());
    doReturn(SECRET).when(decryptionService).getDecryptedValue(any());
    initMocks(this);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testHashedPayloadMatches() throws IOException {
    TriggerAuthenticationTaskParams taskParams = createTaskParams(GitRepoType.GITHUB, HASHED_PAYLOAD);
    TriggerAuthenticationTaskResponse taskResponse =
        (TriggerAuthenticationTaskResponse) triggerAuthenticationTask.run(taskParams);
    assertThat(taskResponse).isNotNull();
    assertThat(taskResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(taskResponse.getTriggersAuthenticationStatus().get(0)).isTrue();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testHashedPayloadDoesNotMatch() throws IOException {
    TriggerAuthenticationTaskParams taskParams = createTaskParams(GitRepoType.GITHUB, "sha256=12345");
    TriggerAuthenticationTaskResponse taskResponse =
        (TriggerAuthenticationTaskResponse) triggerAuthenticationTask.run(taskParams);
    assertThat(taskResponse).isNotNull();
    assertThat(taskResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(taskResponse.getTriggersAuthenticationStatus().get(0)).isFalse();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testWebHookAuthenticationWithBitbucket() throws IOException {
    TriggerAuthenticationTaskParams taskParams = createTaskParams(GitRepoType.BITBUCKET, HASHED_PAYLOAD);
    assertThatThrownBy(() -> triggerAuthenticationTask.run(taskParams))
        .isEqualToComparingFieldByField(
            new UnsupportedOperationException("Only GitHub trigger authentication is supported"));
  }

  private TriggerAuthenticationTaskParams createTaskParams(GitRepoType gitRepoType, String hashedPayload)
      throws IOException {
    List<EncryptedDataDetail> encryptedDataDetails =
        Collections.singletonList(EncryptedDataDetail.builder().fieldName("secretRef").build());
    SecretRefData secretRefData = SecretRefHelper.createSecretRef("my_secret");
    WebhookEncryptedSecretDTO webhookEncryptedSecretDTO =
        WebhookEncryptedSecretDTO.builder().secretRef(secretRefData).build();
    List<WebhookSecretData> webhookSecretData =
        Collections.singletonList(WebhookSecretData.builder()
                                      .webhookEncryptedSecretDTO(webhookEncryptedSecretDTO)
                                      .encryptedDataDetails(encryptedDataDetails)
                                      .build());
    String githubPayload = IOUtils.resourceToString(RESOURCE_FILE, StandardCharsets.UTF_8, getClass().getClassLoader());
    return TriggerAuthenticationTaskParams.builder()
        .eventPayload(githubPayload)
        .hashedPayload(hashedPayload)
        .gitRepoType(gitRepoType)
        .webhookSecretData(webhookSecretData)
        .build();
  }
}
