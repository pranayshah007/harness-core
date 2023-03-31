/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessModule._360_CG_MANAGER;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.api.EmailStateExecutionData.Builder.anEmailStateExecutionData;
import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.ContextElementParamMapperFactory;
import software.wings.api.EmailStateExecutionData;
import software.wings.api.HostElement;
import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.persistence.mail.EmailData;
import software.wings.service.impl.UserServiceImpl;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.EmailNotificationService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * The Class EmailStateTest.
 *
 * @author paggarwal.
 */
@OwnedBy(CDC)
@TargetModule(_360_CG_MANAGER)
public class EmailStateTest extends WingsBaseTest {
  private static final Splitter COMMA_SPLITTER = Splitter.on(",").omitEmptyStrings().trimResults();
  private static final String BASE_URL = "https://env.harness.io/";
  private static final String stateName = "emailState1";
  private static final String EMAIL = "user@test.com";
  private static final String USERNAME = "username";
  private static final String UUID = RandomStringUtils.randomAlphanumeric(32);
  private static final EmailStateExecutionData.Builder expected = anEmailStateExecutionData()
                                                                      .withToAddress("to1, to2")
                                                                      .withCcAddress("cc1, cc2")
                                                                      .withSubject("subject")
                                                                      .withBody("body");
  private final EmailData emailData = EmailData.builder()
                                          .accountId(ACCOUNT_ID)
                                          .to(Lists.newArrayList("to1", "to2"))
                                          .cc(Lists.newArrayList("cc1", "cc2"))
                                          .subject("subject")
                                          .body("body")
                                          .build();
  @Inject private Injector injector;

  @Mock private EmailNotificationService emailNotificationService;
  @Mock private AccountService accountService;

  @InjectMocks private EmailState emailState = new EmailState(stateName);

  @Mock private MainConfiguration configuration;
  @Mock private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Mock private UserServiceImpl userServiceImpl;
  @Mock private ManagerExpressionEvaluator evaluator;

  private ExecutionContextImpl context;
  private Account account;
  private Account wrongAccount;

  /**
   * Sets the up context and state.
   */
  @Before
  public void setUpContextAndState() {
    StateExecutionInstance stateExecutionInstance = new StateExecutionInstance();
    stateExecutionInstance.setUuid(generateUuid());
    stateExecutionInstance.setDisplayName(stateName);

    context = new ExecutionContextImpl(stateExecutionInstance, null, injector);

    WorkflowStandardParams workflowStandardParams = new WorkflowStandardParams();
    on(workflowStandardParams).set("currentUser", EmbeddedUser.builder().name("admin").build());
    context.pushContextElement(workflowStandardParams);

    WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService =
        mock(WorkflowStandardParamsExtensionService.class);
    doReturn(anApplication().accountId(ACCOUNT_ID).uuid(APP_ID).build())
        .when(workflowStandardParamsExtensionService)
        .getApp(workflowStandardParams);
    doReturn(anEnvironment().uuid(ENV_ID).build())
        .when(workflowStandardParamsExtensionService)
        .getEnv(workflowStandardParams);
    on(context).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);

    ContextElementParamMapperFactory contextElementParamMapperFactory = new ContextElementParamMapperFactory(
        subdomainUrlHelper, null, null, null, null, null, null, workflowStandardParamsExtensionService);
    on(context).set("contextElementParamMapperFactory", contextElementParamMapperFactory);

    HostElement host = HostElement.builder().build();
    host.setHostName("app123.application.com");
    context.pushContextElement(host);

    emailState.setToAddress("to1,to2");
    emailState.setCcAddress("cc1,cc2");

    PortalConfig portalConfig = new PortalConfig();
    portalConfig.setUrl(BASE_URL);
    when(configuration.getPortal()).thenReturn(portalConfig);
    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn("baseUrl");

    User user = User.Builder.anUser().uuid(UUID).name(USERNAME).email(EMAIL).emailVerified(true).build();
    when(userServiceImpl.getUserWithAcceptedInviteByEmail(any(), any())).thenReturn(user);

    account = anAccount().withUuid(ACCOUNT_ID).build();
    wrongAccount = anAccount().withUuid("WRONG_ID").build();
  }

  /**
   * Should send email.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSendEmail() {
    emailState.setBody("body");
    emailState.setSubject("subject");

    ExecutionResponse executionResponse = emailState.execute(context);
    assertThat(executionResponse).extracting(ExecutionResponse::getExecutionStatus).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getStateExecutionData())
        .isInstanceOf(EmailStateExecutionData.class)
        .isEqualTo(expected.but().build());
    assertThat(executionResponse.getErrorMessage()).isNull();
    assertThat(emailState.getBody()).isNotEmpty();
    assertThat(emailState.getSubject()).isNotEmpty();
    assertThat(emailState.getToAddress()).isNotEmpty();
    assertThat(emailState.getCcAddress()).isNotEmpty();
    assertThat(emailState.isIgnoreDeliveryFailure()).isTrue();

    verify(emailNotificationService).send(emailData);
  }

  /**
   * Should evaluate context elements for email subject and body.
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldEvaluateContextElementsForEmailSubjectAndBody() {
    emailState.setBody("Deployed to host ${host.hostName}");
    emailState.setSubject("Deployed ${host.hostName}");

    ExecutionResponse executionResponse = emailState.execute(context);
    assertThat(executionResponse).extracting(ExecutionResponse::getExecutionStatus).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getStateExecutionData())
        .isInstanceOf(EmailStateExecutionData.class)
        .isEqualTo(expected.but()
                       .withSubject("Deployed app123.application.com")
                       .withBody("Deployed to host app123.application.com")
                       .build());
    assertThat(executionResponse.getErrorMessage()).isNull();

    verify(emailNotificationService)
        .send(

            EmailData.builder()
                .to(Lists.newArrayList("to1", "to2"))
                .accountId(ACCOUNT_ID)
                .cc(Lists.newArrayList("cc1", "cc2"))
                .subject("Deployed app123.application.com")
                .body("Deployed to host app123.application.com")
                .build());
  }

  /**
   * Should capture error message when failed to send email.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldCaptureErrorMessageWhenFailedToSendEmail() {
    doThrow(new RuntimeException("Test exception")).when(emailNotificationService).send(emailData);

    emailState.setBody("body");
    emailState.setSubject("subject");

    ExecutionResponse executionResponse = emailState.execute(context);
    assertThat(executionResponse).extracting(ExecutionResponse::getExecutionStatus).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getStateExecutionData())
        .isInstanceOf(EmailStateExecutionData.class)
        .isEqualTo(expected.but().build());
    assertThat(executionResponse.getErrorMessage()).isNotNull().isEqualTo("RuntimeException: Test exception");

    verify(emailNotificationService).send(emailData);
  }

  /**
   * Should return execution result as error when not ignored.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldReturnExecutionResultAsErrorWhenNotIgnored() {
    doThrow(new RuntimeException("Test exception")).when(emailNotificationService).send(emailData);

    emailState.setBody("body");
    emailState.setSubject("subject");
    emailState.setIgnoreDeliveryFailure(false);

    ExecutionResponse executionResponse = emailState.execute(context);
    assertThat(executionResponse).extracting(ExecutionResponse::getExecutionStatus).isEqualTo(ExecutionStatus.ERROR);
    assertThat(executionResponse.getStateExecutionData())
        .isInstanceOf(EmailStateExecutionData.class)
        .isEqualTo(expected.but().build());
    assertThat(executionResponse.getErrorMessage()).isNotNull().isEqualTo("RuntimeException: Test exception");

    verify(emailNotificationService).send(emailData);
  }

  /**
   * Should render deployment triggered by for email subject and body.
   */
  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldRenderDeploymentTriggeredBy() {
    emailState.setBody("Deployment triggered by: ${deploymentTriggeredBy}");
    emailState.setSubject("Deployment triggered by: ${deploymentTriggeredBy}");

    ExecutionResponse executionResponse = emailState.execute(context);
    assertThat(executionResponse).extracting(ExecutionResponse::getExecutionStatus).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getStateExecutionData())
        .isInstanceOf(EmailStateExecutionData.class)
        .isEqualTo(expected.but()
                       .withSubject("Deployment triggered by: admin")
                       .withBody("Deployment triggered by: admin")
                       .build());
    assertThat(executionResponse.getErrorMessage()).isNull();

    verify(emailNotificationService)
        .send(EmailData.builder()
                  .to(Lists.newArrayList("to1", "to2"))
                  .accountId(ACCOUNT_ID)
                  .cc(Lists.newArrayList("cc1", "cc2"))
                  .subject("Deployment triggered by: admin")
                  .body("Deployment triggered by: admin")
                  .build());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldRenderWorkflowVariables() {
    on(context).set("evaluator", evaluator);

    emailState.setToAddress("${workflow.variables.var1}");
    emailState.setCcAddress("${workflow.variables.var2}");
    emailState.setBody("body");
    emailState.setSubject("subject");

    when(evaluator.substitute(eq("${workflow.variables.var1}"), any(), any(), any())).thenReturn("to1");
    when(evaluator.substitute(eq("${workflow.variables.var2}"), any(), any(), any())).thenReturn("cc1");
    when(evaluator.substitute(eq("body"), any(), any(), any())).thenReturn("body");
    when(evaluator.substitute(eq("subject"), any(), any(), any())).thenReturn("subject");

    ExecutionResponse executionResponse = emailState.execute(context);
    assertThat(executionResponse).extracting(ExecutionResponse::getExecutionStatus).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getStateExecutionData())
        .isInstanceOf(EmailStateExecutionData.class)
        .isEqualTo(
            expected.but().withToAddress("to1").withCcAddress("cc1").withSubject("subject").withBody("body").build());
    assertThat(executionResponse.getErrorMessage()).isNull();

    verify(emailNotificationService)
        .send(EmailData.builder()
                  .to(Lists.newArrayList("to1"))
                  .cc(Lists.newArrayList("cc1"))
                  .accountId(ACCOUNT_ID)
                  .subject("subject")
                  .body("body")
                  .build());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void sendEmailWithInvalidToAddressTest() {
    emailState.setToAddress("toAddress1, toAddress2");
    emailState.setCcAddress("");
    emailState.setBody("body");
    emailState.setSubject("subject");

    User user1 = User.Builder.anUser().uuid(UUID).name(USERNAME).email(EMAIL).accounts(Arrays.asList(account)).build();
    User user2 =
        User.Builder.anUser().uuid(UUID).name(USERNAME).email(EMAIL).accounts(Arrays.asList(wrongAccount)).build();
    when(userServiceImpl.getUserWithAcceptedInviteByEmail(any(), any())).thenReturn(user1, null);

    ExecutionResponse executionResponse = emailState.execute(context);
    assertThat(executionResponse).extracting(ExecutionResponse::getExecutionStatus).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getStateExecutionData().getErrorMsg())
        .isEqualTo("Email was not sent to the following unregistered addresses: toAddress2");
    assertThat(emailState.getBody()).isNotEmpty();
    assertThat(emailState.getSubject()).isNotEmpty();
    assertThat(emailState.getToAddress()).isNotEmpty();
    assertThat(emailState.getCcAddress()).isEmpty();
    assertThat(emailState.getToAddress()).isEqualTo("toAddress1");
    assertThat(emailState.isIgnoreDeliveryFailure()).isTrue();

    verify(emailNotificationService).send(getEmailData(Lists.newArrayList("toAddress1"), Collections.emptyList()));
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void sendEmailWithInvalidCcAddressTest() {
    emailState.setToAddress("");
    emailState.setCcAddress("ccAddress1, ccAddress2");
    emailState.setBody("body");
    emailState.setSubject("subject");

    User user1 =
        User.Builder.anUser().uuid(UUID).name(USERNAME).email(EMAIL).accounts(Arrays.asList(wrongAccount)).build();
    User user2 = User.Builder.anUser().uuid(UUID).name(USERNAME).email(EMAIL).accounts(Arrays.asList(account)).build();
    when(userServiceImpl.getUserWithAcceptedInviteByEmail(any(), any())).thenReturn(null, user2);

    ExecutionResponse executionResponse = emailState.execute(context);
    assertThat(executionResponse).extracting(ExecutionResponse::getExecutionStatus).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getStateExecutionData().getErrorMsg())
        .isEqualTo("Email was not sent to the following unregistered addresses: ccAddress1");
    assertThat(emailState.getBody()).isNotEmpty();
    assertThat(emailState.getSubject()).isNotEmpty();
    assertThat(emailState.getCcAddress()).isNotEmpty();
    assertThat(emailState.getCcAddress()).isEqualTo("ccAddress2");
    assertThat(emailState.getToAddress()).isEmpty();
    assertThat(emailState.isIgnoreDeliveryFailure()).isTrue();

    verify(emailNotificationService).send(getEmailData(Collections.emptyList(), Lists.newArrayList("ccAddress2")));
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void skipSendEmailWithAllInvalidAddressTest() {
    emailState.setToAddress("toAddress1");
    emailState.setCcAddress("ccAddress1");
    emailState.setBody("body");
    emailState.setSubject("subject");

    User user1 =
        User.Builder.anUser().uuid(UUID).name(USERNAME).email(EMAIL).accounts(Arrays.asList(wrongAccount)).build();
    User user2 =
        User.Builder.anUser().uuid(UUID).name(USERNAME).email(EMAIL).accounts(Arrays.asList(wrongAccount)).build();
    when(userServiceImpl.getUserWithAcceptedInviteByEmail(any(), any())).thenReturn(null, null);

    ExecutionResponse executionResponse = emailState.execute(context);
    assertThat(executionResponse).extracting(ExecutionResponse::getExecutionStatus).isEqualTo(ExecutionStatus.SKIPPED);
    assertThat(executionResponse.getStateExecutionData().getErrorMsg())
        .isEqualTo("Email was not sent to the following unregistered addresses: toAddress1, ccAddress1");
    assertThat(emailState.getBody()).isNotEmpty();
    assertThat(emailState.getSubject()).isNotEmpty();
    assertThat(emailState.getCcAddress()).isEmpty();
    assertThat(emailState.getToAddress()).isEmpty();
    assertThat(emailState.isIgnoreDeliveryFailure()).isTrue();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void sendEmailWithNullToAddressTest() {
    emailState.setToAddress(null);
    emailState.setCcAddress("ccAddress1, ccAddress2");
    emailState.setBody("body");
    emailState.setSubject("subject");

    User user1 = User.Builder.anUser().uuid(UUID).name(USERNAME).email(EMAIL).accounts(Arrays.asList(account)).build();
    User user2 = User.Builder.anUser().uuid(UUID).name(USERNAME).email(EMAIL).accounts(Arrays.asList(account)).build();
    when(userServiceImpl.getUserWithAcceptedInviteByEmail(any(), any())).thenReturn(user1, user2);

    ExecutionResponse executionResponse = emailState.execute(context);
    assertThat(executionResponse).extracting(ExecutionResponse::getExecutionStatus).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(emailState.getBody()).isNotEmpty();
    assertThat(emailState.getSubject()).isNotEmpty();
    assertThat(emailState.getCcAddress()).isNotEmpty();
    assertThat(emailState.getCcAddress()).isEqualTo("ccAddress1, ccAddress2");
    assertThat(emailState.getToAddress()).isEmpty();
    assertThat(emailState.isIgnoreDeliveryFailure()).isTrue();

    verify(emailNotificationService)
        .send(getEmailData(Collections.emptyList(), Lists.newArrayList("ccAddress1", "ccAddress2")));
  }

  private EmailData getEmailData(List<String> toAddress, List<String> ccAddress) {
    return EmailData.builder()
        .accountId(ACCOUNT_ID)
        .to(toAddress)
        .cc(ccAddress)
        .subject("subject")
        .body("body")
        .build();
  }
}
