/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.email;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.NotificationTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.remote.dto.EmailDTO;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.remote.client.NGRestUtils;
import io.harness.serializer.JsonUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.executables.PipelineSyncExecutable;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class EmailStep extends PipelineSyncExecutable {
  @Inject private NotificationClient notificationClient;
  public static final StepType STEP_TYPE = StepSpecTypeConstants.EMAIL_STEP_TYPE;
  static final String EMAIL_TO_NON_HARNESS_USERS_SETTING_KEY = "email_to_non_harness_users";
  static final String EMAIL_TO_NON_HARNESS_USERS_TRUE_VALUE = "true";

  @Inject private KryoSerializer kryoSerializer;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private NGSettingsClient settingsClient;

  @Override
  public List<String> getLogKeys(Ambiance ambiance) {
    // TODO need to figure out how this should work...
    return StepUtils.generateLogKeys(ambiance, new ArrayList<>());
  }

  @Override
  public StepResponse executeSyncAfterRbac(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    long startTime = System.currentTimeMillis();
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, null, true);
    EmailStepParameters emailStepParameters = (EmailStepParameters) stepParameters.getSpec();
    String toMail = emailStepParameters.to.getValue();
    String ccMail = emailStepParameters.cc.getValue();
    Set<String> toRecipients = Collections.emptySet();
    Set<String> ccRecipients = Collections.emptySet();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String notificationId = generateUuid();

    if (StringUtils.isNotBlank(toMail)) {
      toRecipients = Stream.of(toMail.trim().split("\\s*,\\s*")).collect(Collectors.toSet());
    }
    if (StringUtils.isNotBlank(ccMail)) {
      ccRecipients = Stream.of(ccMail.trim().split("\\s*,\\s*")).collect(Collectors.toSet());
    }
    if (emailStepParameters.subject == null || StringUtils.isBlank(emailStepParameters.subject.getValue())) {
      throw new InvalidRequestException("Email subject cannot be blank");
    }
    if (emailStepParameters.body == null || StringUtils.isBlank(emailStepParameters.body.getValue())) {
      throw new InvalidRequestException("Email body cannot be blank");
    }

    String settingValue = "";
    try {
      settingValue = NGRestUtils
                         .getResponse(settingsClient.getSetting(EMAIL_TO_NON_HARNESS_USERS_SETTING_KEY,
                             AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
                             AmbianceUtils.getProjectIdentifier(ambiance)))
                         .getValue();
    } catch (Exception ex) {
      log.error("Failed to fetch setting value for {}", EMAIL_TO_NON_HARNESS_USERS_SETTING_KEY, ex);
    }

    EmailDTO emailDTO = EmailDTO.builder()
                            .toRecipients(toRecipients)
                            .ccRecipients(ccRecipients)
                            .body(emailStepParameters.body.getValue())
                            .subject(emailStepParameters.subject.getValue())
                            .accountId(accountId)
                            .notificationId(notificationId)
                            .sendToNonHarnessRecipients(EMAIL_TO_NON_HARNESS_USERS_TRUE_VALUE.equals(settingValue))
                            .build();
    logCallback.saveExecutionLog(String.format("Email step execution started"));
    try {
      Response<ResponseDTO<NotificationTaskResponse>> response = notificationClient.sendEmail(emailDTO);

      if (!response.isSuccessful()) {
        logCallback.saveExecutionLog(
            String.format("Failed to send the email"), LogLevel.INFO, CommandExecutionStatus.FAILURE);
        ErrorDTO responseDTO =
            JsonUtils.asObjectWithExceptionHandlingType(response.errorBody().string(), ErrorDTO.class);
        FailureData failureData = FailureData.newBuilder()
                                      .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                      .setLevel(io.harness.eraro.Level.ERROR.name())
                                      .setCode(GENERAL_ERROR.name())
                                      .setMessage(responseDTO.getMessage())
                                      .build();
        return StepResponse.builder()
            .status(Status.FAILED)
            .failureInfo(FailureInfo.newBuilder().addFailureData(failureData).build())
            .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                            .setStatus(UnitStatus.FAILURE)
                                                            .setStartTime(startTime)
                                                            .setEndTime(System.currentTimeMillis())
                                                            .build()))
            .build();
      }
      if (response.body().getStatus() == io.harness.ng.core.Status.SUCCESS
          && StringUtils.isNotBlank(response.body().getData().getErrorMessage())) {
        logCallback.saveExecutionLog(String.format(response.body().getData().getErrorMessage()));
      }

    } catch (IOException e) {
      logCallback.saveExecutionLog(String.format("Failed to send the email. The reasons could be -\n"
                                       + "- The SMTP server may not be setup correctly(if configured) \n"
                                       + "- Delegate unable to reach custom SMTP server(if configured)\n"
                                       + "- Something went wrong on Harness's end."),
          LogLevel.INFO, CommandExecutionStatus.FAILURE);
      log.error("Not able to send emails", e);
      return StepResponse.builder()
          .status(Status.FAILED)
          .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                          .setStatus(UnitStatus.FAILURE)
                                                          .setStartTime(startTime)
                                                          .setEndTime(System.currentTimeMillis())
                                                          .build()))
          .build();
    }

    logCallback.saveExecutionLog("Email step execution completed", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(YAMLFieldNameConstants.OUTPUT)
                         .outcome(EmailOutcome.builder().notificationId(notificationId).build())
                         .build())
        .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                        .setStatus(UnitStatus.SUCCESS)
                                                        .setStartTime(startTime)
                                                        .setEndTime(System.currentTimeMillis())
                                                        .build()))
        .build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
