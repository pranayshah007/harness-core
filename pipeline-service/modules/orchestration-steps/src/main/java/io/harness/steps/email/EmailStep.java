/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.email;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.NotificationTaskResponse;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.remote.dto.EmailDTO;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;

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
public class EmailStep implements SyncExecutable<StepElementParameters> {
  @Inject private NotificationClient notificationClient;
  public static final StepType STEP_TYPE = StepSpecTypeConstants.EMAIL_STEP_TYPE;

  @Inject private KryoSerializer kryoSerializer;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Override
  public List<String> getLogKeys(Ambiance ambiance) {
    // TODO need to figure out how this should work...
    return StepUtils.generateLogKeys(ambiance, new ArrayList<>());
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, null, true);
    logCallback.saveExecutionLog("hello");
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
    EmailDTO emailDTO = EmailDTO.builder()
                            .toRecipients(toRecipients)
                            .ccRecipients(ccRecipients)
                            .body(emailStepParameters.body.getValue())
                            .subject(emailStepParameters.subject.getValue())
                            .accountId(accountId)
                            .notificationId(notificationId) // testing purpose
                            .build();
    NotificationTaskResponse notificationTaskResponse = null;
    try {
      Response<ResponseDTO<NotificationTaskResponse>> response = notificationClient.sendEmail(emailDTO);
      logCallback.saveExecutionLog(
          String.format("Email Response is %s .", response.body().getData().getErrorMessage()));
    } catch (IOException e) {
    }

    /* StepResponse.builder()
        .status(Status.SUCCEEDED)
        .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                        .setUnitName(INFRASTRUCTURE_COMMAND_UNIT)
                                                        .setStatus(UnitStatus.SUCCESS)
                                                        .setStartTime(startTime)
                                                        .setEndTime(System.currentTimeMillis())
                                                        .build()))
                                                        */
    return StepResponse.builder()
        .stepOutcome(StepResponse.StepOutcome.builder().name(YAMLFieldNameConstants.OUTPUT).outcome(null).build())
        .status(Status.SUCCEEDED)
        .build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  //  @Override
  //  public Class<EmailStepParameters> getStepParametersClass() {
  //    return EmailStepParameters.class;
  //  }

  //  @Override
  //  public StepResponse executeSync(Ambiance ambiance, EmailStepParameters emailStepParameters, StepInputPackage
  //  inputPackage, PassThroughData passThroughData)
  //  {
  //    int socketTimeoutMillis = (int) NGTimeConversionHelper.convertTimeStringToMilliseconds("10m");
  //    if (stepParameters.getTimeout() != null && stepParameters.getTimeout().getValue() != null) {
  //      socketTimeoutMillis =
  //              (int) NGTimeConversionHelper.convertTimeStringToMilliseconds(stepParameters.getTimeout().getValue());
  //    }
  //    String toMail = emailStepParameters.to.getValue();
  //    String ccMail = emailStepParameters.cc.getValue();
  //    Set<String> toRecipients = Collections.emptySet();
  //    Set<String> ccRecipients = Collections.emptySet();
  //    if(StringUtils.isNotBlank(toMail))
  //    {
  //      toRecipients = Stream.of(toMail.trim().split("\\s*,\\s*"))
  //              .collect(Collectors.toSet());
  //    }
  //    if(StringUtils.isNotBlank(ccMail)){
  //      ccRecipients = Stream.of(ccMail.trim().split("\\s*,\\s*"))
  //              .collect(Collectors.toSet());}
  //    EmailDTO emailDTO = EmailDTO.builder()
  //            .toRecipients(toRecipients)
  //            .ccRecipients(ccRecipients)
  //            .body(emailStepParameters.body.getValue())
  //            .subject(emailStepParameters.subject.getValue())
  //            .accountId("kmpySmUISimoRrJL6NL73w") //testing purpose
  //            .notificationId("notificationId")  //testing purpose
  //            .build();
  //    return null;
  //
  //  }
}
