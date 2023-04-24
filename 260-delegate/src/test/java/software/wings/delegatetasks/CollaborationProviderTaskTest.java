/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.RUSHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.helpers.ext.external.comm.CollaborationProviderRequest;
import software.wings.helpers.ext.external.comm.CollaborationProviderRequest.CommunicationType;
import software.wings.helpers.ext.external.comm.CollaborationProviderResponse;
import software.wings.helpers.ext.external.comm.EmailRequest;
import software.wings.helpers.ext.external.comm.handlers.EmailHandler;
import software.wings.helpers.ext.mail.EmailData;
import software.wings.helpers.ext.mail.Mailer;
import software.wings.helpers.ext.mail.SmtpConfig;

import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class CollaborationProviderTaskTest extends WingsBaseTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock Mailer mailer;

  @InjectMocks EmailHandler emailHandler;

  @InjectMocks
  private CollaborationProviderTask collaborationProviderTask = new CollaborationProviderTask(
      DelegateTaskPackage.builder()
          .delegateId("delid1")
          .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
          .build(),
      null, notifyResponseData -> {}, () -> true);

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testEmailNotification() {
    EmailRequest emailRequest = new EmailRequest();
    collaborationProviderTask.run(new Object[] {emailRequest});
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testFailure() {
    doThrow(new WingsException(ErrorCode.EMAIL_FAILED))
        .when(mailer)
        .send(any(SmtpConfig.class), any(List.class), any(EmailData.class));
    EmailRequest emailRequest = new EmailRequest();
    DelegateResponseData response = collaborationProviderTask.run(new Object[] {emailRequest});
    assertThat(response).isInstanceOf(CollaborationProviderResponse.class);
    assertThat(((CollaborationProviderResponse) response).getStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testAllSupportedCommunicationTypes() {
    CollaborationProviderRequest request = Mockito.mock(CollaborationProviderRequest.class);
    for (CommunicationType type : CommunicationType.values()) {
      when(request.getCommunicationType()).thenReturn(type);
      collaborationProviderTask.run(new Object[] {request});
    }
  }
}
