/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 *
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.api.EmailStateExecutionData.Builder.anEmailStateExecutionData;

import static java.util.stream.Collectors.partitioningBy;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.data.algorithm.HashGenerator;
import io.harness.exception.ExceptionUtils;
import io.harness.reflection.ExpressionReflectionUtils;

import software.wings.api.EmailStateExecutionData;
import software.wings.beans.User;
import software.wings.expression.ManagerPreviewExpressionEvaluator;
import software.wings.helpers.ext.mail.EmailData;
import software.wings.helpers.ext.mail.EmailData.EmailDataBuilder;
import software.wings.service.impl.UserServiceImpl;
import software.wings.service.intfc.EmailNotificationService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateExecutionContext;
import software.wings.sm.StateType;
import software.wings.utils.EmailParams;

import com.github.reinert.jjschema.Attributes;
import com.google.common.base.Splitter;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.annotations.Transient;

/**
 * The Class EmailState.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@Attributes
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class EmailState extends State {
  private static final Splitter COMMA_SPLITTER = Splitter.on(",").omitEmptyStrings().trimResults();
  private static final String EMAIL_NOT_SENT_MESSAGE = "Email was not sent to the following unregistered addresses: %s";

  @Attributes(required = true, title = "To") private String toAddress;
  @Attributes(title = "CC") private String ccAddress;
  @Attributes(required = true, title = "Subject") private String subject;
  @Attributes(title = "Body") private String body;
  @Attributes(title = "Ignore delivery failure?") private Boolean ignoreDeliveryFailure = true;

  @Transient @Inject private EmailNotificationService emailNotificationService;
  @Inject UserServiceImpl userServiceImpl;

  /**
   * Instantiates a new email state.
   *
   * @param name the name
   */
  public EmailState(String name) {
    super(name, StateType.EMAIL.name());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    ExecutionResponseBuilder executionResponseBuilder = ExecutionResponse.builder();
    EmailStateExecutionData emailStateExecutionData;
    String accountId = context.getAccountId();

    toAddress = renderExpression(toAddress, context);
    ccAddress = renderExpression(ccAddress, context);

    Map<Boolean, List<String>> toAddressMap = getEmailAddressMap(toAddress, accountId);
    Map<Boolean, List<String>> ccAddressMap = getEmailAddressMap(ccAddress, accountId);
    toAddress = StringUtils.join(toAddressMap.get(true), ", ");
    ccAddress = StringUtils.join(ccAddressMap.get(true), ", ");

    List<String> unregisteredAddressList =
        Stream.concat(toAddressMap.get(false).stream(), ccAddressMap.get(false).stream())
            .distinct()
            .collect(Collectors.toList());
    String unregisteredAddress = StringUtils.join(unregisteredAddressList, ", ");

    emailStateExecutionData = getEmailStateExecutionData(toAddress, ccAddress, subject, body);

    try {
      ManagerPreviewExpressionEvaluator expressionEvaluator = new ManagerPreviewExpressionEvaluator();

      if (StringUtils.isNotBlank(unregisteredAddress)) {
        log.warn(String.format(EMAIL_NOT_SENT_MESSAGE, unregisteredAddress));
        emailStateExecutionData.setErrorMsg(String.format(EMAIL_NOT_SENT_MESSAGE, unregisteredAddress));
        executionResponseBuilder.errorMessage(String.format(EMAIL_NOT_SENT_MESSAGE, unregisteredAddress));
      }

      int expressionFunctorToken = HashGenerator.generateIntegerHash();
      if (StringUtils.isNotBlank(toAddress) || StringUtils.isNotBlank(ccAddress)) {
        String evaluatedSubject = context.renderExpression(subject);
        String evaluatedBody = context.renderExpression(body);

        EmailParams emailParams = EmailParams.builder().body(body).subject(subject).build();

        context.resetPreparedCache();

        ExpressionReflectionUtils.applyExpression(emailParams,
            (secretMode, value)
                -> context.renderExpression(value,
                    StateExecutionContext.builder()
                        .stateExecutionData(emailStateExecutionData)
                        .adoptDelegateDecryption(true)
                        .expressionFunctorToken(expressionFunctorToken)
                        .build()));

        emailStateExecutionData.setSubject(
            expressionEvaluator.substitute(emailParams.getSubject(), Collections.emptyMap()));
        emailStateExecutionData.setBody(expressionEvaluator.substitute(emailParams.getBody(), Collections.emptyMap()));
        log.debug("Email Notification - subject:{}, body:{}", evaluatedSubject, evaluatedBody);

        if (StringUtils.isNotBlank(toAddress)) {
          emailStateExecutionData.setToAddress(expressionEvaluator.substitute(toAddress, Collections.emptyMap()));
          emailStateExecutionData.setCcAddress(expressionEvaluator.substitute(ccAddress, Collections.emptyMap()));

          emailNotificationService.send(getEmailData(
              toAddress, ccAddress, evaluatedSubject, evaluatedBody, accountId, context.getWorkflowExecutionId()));

        } else if (StringUtils.isNotBlank(ccAddress)) {
          emailStateExecutionData.setCcAddress(expressionEvaluator.substitute(ccAddress, Collections.emptyMap()));

          emailNotificationService.send(getEmailData(
              toAddress, ccAddress, evaluatedSubject, evaluatedBody, accountId, context.getWorkflowExecutionId()));
        }
        executionResponseBuilder.executionStatus(ExecutionStatus.SUCCESS);

      } else {
        executionResponseBuilder.executionStatus(ExecutionStatus.SKIPPED);
      }
    } catch (Exception e) {
      executionResponseBuilder.errorMessage(
          e.getCause() == null ? ExceptionUtils.getMessage(e) : ExceptionUtils.getMessage(e.getCause()));
      executionResponseBuilder.executionStatus(ignoreDeliveryFailure ? ExecutionStatus.SUCCESS : ExecutionStatus.ERROR);
      log.error("Exception while sending email", e);
    }
    executionResponseBuilder.stateExecutionData(emailStateExecutionData);
    return executionResponseBuilder.build();
  }

  private String renderExpression(String addresses, ExecutionContext context) {
    if (StringUtils.isNotBlank(addresses)) {
      return context.renderExpression(addresses);
    }
    return null;
  }

  private EmailStateExecutionData getEmailStateExecutionData(
      String toAddress, String ccAddress, String subject, String body) {
    if (StringUtils.isNotBlank(toAddress)) {
      return anEmailStateExecutionData()
          .withBody(body)
          .withToAddress(toAddress)
          .withCcAddress(ccAddress)
          .withSubject(subject)
          .build();
    } else if (StringUtils.isNotBlank(ccAddress)) {
      return anEmailStateExecutionData().withBody(body).withCcAddress(ccAddress).withSubject(subject).build();
    } else {
      return anEmailStateExecutionData().build();
    }
  }

  private EmailData getEmailData(String evaluatedTo, String evaluatedCc, String evaluatedSubject, String evaluatedBody,
      String accountId, String workflowExecutionId) {
    EmailDataBuilder emailDataBuilder = EmailData.builder()
                                            .subject(evaluatedSubject)
                                            .body(evaluatedBody)
                                            .accountId(accountId)
                                            .workflowExecutionId(workflowExecutionId);

    if (evaluatedTo != null) {
      emailDataBuilder.to(getEmailAddressList(evaluatedTo));
    }
    if (evaluatedCc != null) {
      emailDataBuilder.cc(getEmailAddressList(evaluatedCc));
    }
    return emailDataBuilder.build();
  }

  private List<String> getEmailAddressList(String address) {
    List addressList = new ArrayList();
    if (isNotEmpty(address)) {
      addressList.addAll(COMMA_SPLITTER.splitToList(address));
    }
    return addressList;
  }

  /**
   * Retrieves map divided on two partitions, the first partition holds registered addresses while the second holds
   * unregistered addresses
   *
   * @param emailList - email list
   * @param accountId - account identifier
   *
   * @return map with two partitions
   */
  private Map<Boolean, List<String>> getEmailAddressMap(String emailList, String accountId) {
    if (emailList != null) {
      return Stream.of(emailList.split(","))
          .filter(StringUtils::isNotBlank)
          .map(String::trim)
          .distinct()
          .collect(partitioningBy(address -> isEmailAddressRegistered(address, accountId)));
    } else {
      return retrieveEmptyAddressMap();
    }
  }

  private boolean isEmailAddressRegistered(String address, String accountId) {
    User user = userServiceImpl.getUserWithAcceptedInviteByEmail(address, accountId);
    return user != null;
  }

  /**
   * Retrieves map with two empty partitions
   *
   * @return map with empty partitions
   */
  private Map<Boolean, List<String>> retrieveEmptyAddressMap() {
    Map<Boolean, List<String>> emptyMap = new HashMap<>();
    emptyMap.put(false, Collections.emptyList());
    emptyMap.put(true, Collections.emptyList());
    return emptyMap;
  }

  /**
   * Handle abort event.
   *
   * @param context the context
   */
  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  /**
   * Gets to address.
   *
   * @return the to address
   */
  public String getToAddress() {
    return toAddress;
  }

  /**
   * Sets to address.
   *
   * @param toAddress the to address
   */
  public void setToAddress(String toAddress) {
    this.toAddress = toAddress;
  }

  /**
   * Gets cc address.
   *
   * @return the cc address
   */
  public String getCcAddress() {
    return ccAddress;
  }

  /**
   * Sets cc address.
   *
   * @param ccAddress the cc address
   */
  public void setCcAddress(String ccAddress) {
    this.ccAddress = ccAddress;
  }

  /**
   * Gets subject.
   *
   * @return the subject
   */
  public String getSubject() {
    return subject;
  }

  /**
   * Sets subject.
   *
   * @param subject the subject
   */
  public void setSubject(String subject) {
    this.subject = subject;
  }

  /**
   * Gets body.
   *
   * @return the body
   */
  public String getBody() {
    return body;
  }

  /**
   * Sets body.
   *
   * @param body the body
   */
  public void setBody(String body) {
    this.body = body;
  }

  /**
   * Is ignore delivery failure boolean.
   *
   * @return the boolean
   */
  public Boolean isIgnoreDeliveryFailure() {
    return ignoreDeliveryFailure;
  }

  /**
   * Sets ignore delivery failure.
   *
   * @param ignoreDeliveryFailure the ignore delivery failure
   */
  public void setIgnoreDeliveryFailure(Boolean ignoreDeliveryFailure) {
    if (ignoreDeliveryFailure != null) {
      this.ignoreDeliveryFailure = ignoreDeliveryFailure;
    }
  }
}
