/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.SATYAM;

import static software.wings.beans.HostConnectionAttributes.Builder.aHostConnectionAttributes;
import static software.wings.beans.HostValidationResponse.Builder.aHostValidationResponse;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.beans.dto.SettingAttribute;
import software.wings.helpers.ext.mail.Mailer;
import software.wings.helpers.ext.mail.SmtpConfig;
import software.wings.settings.validation.ConnectivityValidationDelegateRequest;
import software.wings.settings.validation.SmtpConnectivityValidationAttributes;
import software.wings.settings.validation.SshConnectionConnectivityValidationAttributes;
import software.wings.settings.validation.WinRmConnectivityValidationAttributes;
import software.wings.utils.HostValidationService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ConnectivityValidationTaskTest extends WingsBaseTest {
  @Mock private HostValidationService mockHostValidationService;
  @Mock private Mailer mockMailer;

  @InjectMocks
  private ConnectivityValidationTask task = new ConnectivityValidationTask(
      DelegateTaskPackage.builder()
          .delegateId("delegateid")
          .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
          .build(),
      null, notifyResponseData -> {}, () -> true);

  @Before
  public void setUp() throws Exception {
    on(task).set("hostValidationService", mockHostValidationService);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testRun() {
    ConnectivityValidationDelegateRequest request =
        ConnectivityValidationDelegateRequest.builder()
            .encryptedDataDetails(emptyList())
            .settingAttribute(SettingAttribute.builder()
                                  .accountId(ACCOUNT_ID)
                                  .value(aHostConnectionAttributes().build())
                                  .validationAttributes(
                                      SshConnectionConnectivityValidationAttributes.builder().hostName("host").build())
                                  .build())
            .build();
    doReturn(singletonList(aHostValidationResponse().withHostName("host").withStatus("SUCCESS").build()))
        .when(mockHostValidationService)
        .validateHost(anyList(), any(), anyList(), any(), any());
    task.run(new Object[] {request});
    verify(mockHostValidationService).validateHost(anyList(), any(), anyList(), any(), any());
    request = ConnectivityValidationDelegateRequest.builder()
                  .encryptedDataDetails(emptyList())
                  .settingAttribute(SettingAttribute.builder()
                                        .accountId(ACCOUNT_ID)
                                        .value(WinRmConnectionAttributes.builder().build())
                                        .validationAttributes(
                                            WinRmConnectivityValidationAttributes.builder().hostName("host").build())
                                        .build())
                  .build();
    task.run(new Object[] {request});
    verify(mockHostValidationService, times(2)).validateHost(anyList(), any(), anyList(), any(), any());
    request = ConnectivityValidationDelegateRequest.builder()
                  .encryptedDataDetails(emptyList())
                  .settingAttribute(SettingAttribute.builder()
                                        .accountId(ACCOUNT_ID)
                                        .value(SmtpConfig.builder().build())
                                        .validationAttributes(SmtpConnectivityValidationAttributes.builder()
                                                                  .to("harness@harness.io")
                                                                  .body("body")
                                                                  .subject("subject")
                                                                  .build())
                                        .build())
                  .build();
    task.run(new Object[] {request});
    verify(mockMailer).send(any(), anyList(), any());
  }
}
