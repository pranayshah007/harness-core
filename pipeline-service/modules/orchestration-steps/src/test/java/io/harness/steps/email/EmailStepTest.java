/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.email;

import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.NotificationTaskResponse;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.remote.dto.EmailDTO;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.core.classloader.annotations.PrepareForTest;
import retrofit2.Response;
@OwnedBy(HarnessTeam.CDC)
@PrepareForTest({StepUtils.class})
public class EmailStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @InjectMocks EmailStep emailStep;
  @Mock private NotificationClient notificationClient;

  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private ILogStreamingStepClient iLogStreamingStepClient;
  private static final String INFRASTRUCTURE_COMMAND_UNIT = "Execute";
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testExecuteSyncSuccessWithoutMessage() throws IOException {
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(iLogStreamingStepClient);
    String SUBJECT = "Email Subject";
    String BODY = "Email Body";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "accountId").build();
    EmailStepParameters emailStepParameters =
        EmailStepParameters.builder()
            .to(ParameterField.<String>builder().value("test@harness.io,hello@harness.io").build())
            .subject(ParameterField.<String>builder().value(SUBJECT).build())
            .body(ParameterField.<String>builder().value(BODY).build())
            .cc(ParameterField.<String>builder().value("first@harness.io,second@harness.io").build())
            .build();
    SpecParameters specParameters = (SpecParameters) emailStepParameters;
    Set<String> toRecipients = new HashSet<>();
    Set<String> ccRecipients = new HashSet<>();
    toRecipients.add("test@harness.io");
    toRecipients.add("hello@harness.io");
    ccRecipients.add("first@harness.io");
    ccRecipients.add("second@harness.io");
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(specParameters).build();
    EmailDTO emailDTO = EmailDTO.builder()
                            .toRecipients(toRecipients)
                            .ccRecipients(ccRecipients)
                            .subject(SUBJECT)
                            .body(BODY)
                            .notificationId("notificationId")
                            .accountId("accountId")
                            .build();

    NotificationTaskResponse notificationTaskResponse = NotificationTaskResponse.builder().build();
    ResponseDTO<NotificationTaskResponse> notificationTaskResponseResponseDTO =
        ResponseDTO.newResponse(notificationTaskResponse);
    Response<ResponseDTO<NotificationTaskResponse>> response = Response.success(notificationTaskResponseResponseDTO);
    response.body().setStatus(io.harness.ng.core.Status.FAILURE);
    mockStatic(UUIDGenerator.class);
    Mockito.when(UUIDGenerator.generateUuid()).thenReturn("notificationId");

    doReturn(response).when(notificationClient).sendEmail(any());
    ArgumentCaptor<EmailDTO> argumentCaptor = ArgumentCaptor.forClass(EmailDTO.class);
    StepResponse stepResponse = emailStep.executeSync(ambiance, stepElementParameters, null, null);
    verify(notificationClient).sendEmail(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getAccountId()).isEqualTo(emailDTO.getAccountId());
    assertThat(argumentCaptor.getValue().getBody()).isEqualTo(emailDTO.getBody());
    assertThat(argumentCaptor.getValue().getSubject()).isEqualTo(emailDTO.getSubject());
    assertThat(argumentCaptor.getValue().getToRecipients()).isEqualTo(emailDTO.getToRecipients());
    assertThat(argumentCaptor.getValue().getCcRecipients()).isEqualTo(emailDTO.getCcRecipients());
    assertThat(stepResponse.getStepOutcomes().iterator().next().getOutcome())
        .isEqualTo(EmailOutcome.builder().notificationId("notificationId").build());
    assertThat(stepResponse.getStatus()).isEqualTo(io.harness.pms.contracts.execution.Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testExecuteSyncSuccessWithMessage() throws IOException {
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(iLogStreamingStepClient);
    String SUBJECT = "Email Subject";
    String BODY = "Email Body";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "accountId").build();
    EmailStepParameters emailStepParameters = EmailStepParameters.builder()
                                                  .to(ParameterField.<String>builder().value("test@harness.io").build())
                                                  .subject(ParameterField.<String>builder().value(SUBJECT).build())
                                                  .body(ParameterField.<String>builder().value(BODY).build())
                                                  .cc(ParameterField.<String>builder().value(null).build())
                                                  .build();
    SpecParameters specParameters = (SpecParameters) emailStepParameters;
    Set<String> toRecipients = new HashSet<>();
    Set<String> ccRecipients = new HashSet<>();
    toRecipients.add("test@harness.io");
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(specParameters).build();
    EmailDTO emailDTO = EmailDTO.builder()
                            .toRecipients(toRecipients)
                            .ccRecipients(ccRecipients)
                            .subject(SUBJECT)
                            .body(BODY)
                            .notificationId("notificationId")
                            .accountId("accountId")
                            .build();

    NotificationTaskResponse notificationTaskResponse =
        NotificationTaskResponse.builder().errorMessage("Email Step Response").build();
    ResponseDTO<NotificationTaskResponse> notificationTaskResponseResponseDTO =
        ResponseDTO.newResponse(notificationTaskResponse);
    Response<ResponseDTO<NotificationTaskResponse>> response = Response.success(notificationTaskResponseResponseDTO);
    response.body().setStatus(Status.SUCCESS);
    mockStatic(UUIDGenerator.class);
    Mockito.when(UUIDGenerator.generateUuid()).thenReturn("notificationId");

    doReturn(response).when(notificationClient).sendEmail(any());
    ArgumentCaptor<EmailDTO> argumentCaptor = ArgumentCaptor.forClass(EmailDTO.class);
    StepResponse stepResponse = emailStep.executeSync(ambiance, stepElementParameters, null, null);
    verify(notificationClient).sendEmail(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getAccountId()).isEqualTo(emailDTO.getAccountId());
    assertThat(argumentCaptor.getValue().getBody()).isEqualTo(emailDTO.getBody());
    assertThat(argumentCaptor.getValue().getSubject()).isEqualTo(emailDTO.getSubject());
    assertThat(argumentCaptor.getValue().getToRecipients()).isEqualTo(emailDTO.getToRecipients());
    assertThat(argumentCaptor.getValue().getCcRecipients()).isEqualTo(emailDTO.getCcRecipients());
    assertThat(stepResponse.getStepOutcomes().iterator().next().getOutcome())
        .isEqualTo(EmailOutcome.builder().notificationId("notificationId").build());
    assertThat(stepResponse.getStatus()).isEqualTo(io.harness.pms.contracts.execution.Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testExecuteSyncError() throws IOException {
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(iLogStreamingStepClient);
    String SUBJECT = "Email Subject";
    String BODY = "Email Body";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "accountId").build();
    EmailStepParameters emailStepParameters = EmailStepParameters.builder()
                                                  .to(ParameterField.<String>builder().value("test@harness.io").build())
                                                  .subject(ParameterField.<String>builder().value(SUBJECT).build())
                                                  .body(ParameterField.<String>builder().value(BODY).build())
                                                  .cc(ParameterField.<String>builder().value(null).build())
                                                  .build();
    SpecParameters specParameters = (SpecParameters) emailStepParameters;
    Set<String> toRecipients = new HashSet<>();
    Set<String> ccRecipients = new HashSet<>();
    toRecipients.add("test@harness.io");
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(specParameters).build();
    EmailDTO emailDTO = EmailDTO.builder()
                            .toRecipients(toRecipients)
                            .ccRecipients(ccRecipients)
                            .subject(SUBJECT)
                            .body(BODY)
                            .notificationId("notificationId")
                            .accountId("accountId")
                            .build();
    NotificationTaskResponse notificationTaskResponse =
        NotificationTaskResponse.builder().errorMessage("Email Step Response").build();

    //    ResponseDTO<ErrorDTO> errorDTO = ResponseDTO.newResponse(ErrorDTO.newError(Status.ERROR,UNKNOWN_ERROR,"failed
    //    response"));
    ResponseBody body = ResponseBody.create(MediaType.parse("json"),
        " {\"status\":\"ERROR\",\"code\":\"UNKNOWN_ERROR\",\"message\":\"Emails hello@harness.io are not present in account. No email id encountered.\",\"correlationId\":\"b76b895b-c973-4838-b909-bbd08f47acc0\",\"detailedMessage\":null,\"responseMessages\":[],\"metadata\":null}");
    Response<ResponseDTO<ErrorDTO>> response = Response.error(400, body);
    mockStatic(UUIDGenerator.class);
    Mockito.when(UUIDGenerator.generateUuid()).thenReturn("notificationId");
    doReturn(response).when(notificationClient).sendEmail(any());
    ArgumentCaptor<EmailDTO> argumentCaptor = ArgumentCaptor.forClass(EmailDTO.class);
    StepResponse stepResponse = emailStep.executeSync(ambiance, stepElementParameters, null, null);
    verify(notificationClient).sendEmail(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getAccountId()).isEqualTo(emailDTO.getAccountId());
    assertThat(argumentCaptor.getValue().getBody()).isEqualTo(emailDTO.getBody());
    assertThat(argumentCaptor.getValue().getSubject()).isEqualTo(emailDTO.getSubject());
    assertThat(argumentCaptor.getValue().getToRecipients()).isEqualTo(emailDTO.getToRecipients());
    assertThat(argumentCaptor.getValue().getCcRecipients()).isEqualTo(emailDTO.getCcRecipients());
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage())
        .isEqualTo("Emails hello@harness.io are not present in account. No email id encountered.");
    assertThat(stepResponse.getStatus()).isEqualTo(io.harness.pms.contracts.execution.Status.FAILED);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testExecuteSyncIOException() throws IOException {
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(iLogStreamingStepClient);
    String SUBJECT = "Email Subject";
    String BODY = "Email Body";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "accountId").build();
    EmailStepParameters emailStepParameters = EmailStepParameters.builder()
                                                  .to(ParameterField.<String>builder().value("test@harness.io").build())
                                                  .subject(ParameterField.<String>builder().value(SUBJECT).build())
                                                  .body(ParameterField.<String>builder().value(BODY).build())
                                                  .cc(ParameterField.<String>builder().value(null).build())
                                                  .build();
    SpecParameters specParameters = (SpecParameters) emailStepParameters;
    Set<String> toRecipients = new HashSet<>();
    Set<String> ccRecipients = new HashSet<>();
    toRecipients.add("test@harness.io");
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(specParameters).build();
    EmailDTO emailDTO = EmailDTO.builder()
                            .toRecipients(toRecipients)
                            .ccRecipients(ccRecipients)
                            .subject(SUBJECT)
                            .body(BODY)
                            .notificationId("notificationId")
                            .accountId("accountId")
                            .build();
    NotificationTaskResponse notificationTaskResponse =
        NotificationTaskResponse.builder().errorMessage("Email Step Response").build();
    ResponseDTO<NotificationTaskResponse> notificationTaskResponseResponseDTO =
        ResponseDTO.newResponse(notificationTaskResponse);
    Response<ResponseDTO<NotificationTaskResponse>> response = Response.success(notificationTaskResponseResponseDTO);
    response.body().setStatus(Status.ERROR);
    mockStatic(UUIDGenerator.class);
    Mockito.when(UUIDGenerator.generateUuid()).thenReturn("notificationId");
    doThrow(new IOException()).when(notificationClient).sendEmail(any());
    ArgumentCaptor<EmailDTO> argumentCaptor = ArgumentCaptor.forClass(EmailDTO.class);
    StepResponse stepResponse = emailStep.executeSync(ambiance, stepElementParameters, null, null);
    verify(notificationClient).sendEmail(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getAccountId()).isEqualTo(emailDTO.getAccountId());
    assertThat(argumentCaptor.getValue().getBody()).isEqualTo(emailDTO.getBody());
    assertThat(argumentCaptor.getValue().getSubject()).isEqualTo(emailDTO.getSubject());
    assertThat(argumentCaptor.getValue().getToRecipients()).isEqualTo(emailDTO.getToRecipients());
    assertThat(argumentCaptor.getValue().getCcRecipients()).isEqualTo(emailDTO.getCcRecipients());
    assertThat(stepResponse.getStatus()).isEqualTo(io.harness.pms.contracts.execution.Status.FAILED);
  }
}
