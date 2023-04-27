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
import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.spy;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.ContextElementParamMapperFactory;
import software.wings.api.EmailStateExecutionData;
import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.beans.User;
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

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Injector;
import freemarker.template.TemplateException;
import java.io.IOException;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.mail.EmailException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by peeyushaggarwal on 6/7/16.
 */
@OwnedBy(CDC)
@TargetModule(_360_CG_MANAGER)
public class PauseStateTest extends WingsBaseTest {
  private static final String BASE_URL = "https://env.harness.io/";
  private static final String stateName = "PauseState1";
  private static final String EMAIL = "user@test.com";
  private static final String USERNAME = "username";
  private static final String UUID = RandomStringUtils.randomAlphanumeric(32);
  private static final EmailStateExecutionData.Builder expected =
      EmailStateExecutionData.Builder.anEmailStateExecutionData()
          .withToAddress("to1, to2")
          .withCcAddress("cc1, cc2")
          .withSubject("subject")
          .withBody("body");

  @Inject private Injector injector;

  @Mock private EmailNotificationService emailNotificationService;
  @Mock private AccountService accountService;
  @Mock private SubdomainUrlHelperIntfc subdomainUrlHelper;

  @InjectMocks private PauseState pauseState = new PauseState(stateName);

  @Mock private MainConfiguration configuration;
  @Mock private UserServiceImpl userServiceImpl;

  private ExecutionContextImpl context;

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

    WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService =
        spy(new WorkflowStandardParamsExtensionService(null, accountService, null, null, null, null, null));
    when(workflowStandardParamsExtensionService.getApp(workflowStandardParams))
        .thenReturn(anApplication().accountId(ACCOUNT_ID).uuid(APP_ID).build());
    when(workflowStandardParamsExtensionService.getEnv(workflowStandardParams))
        .thenReturn(anEnvironment().uuid(ENV_ID).build());

    ContextElementParamMapperFactory contextElementParamMapperFactory = new ContextElementParamMapperFactory(
        subdomainUrlHelper, null, null, null, null, null, null, workflowStandardParamsExtensionService);

    context.pushContextElement(workflowStandardParams);
    on(context).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(context).set("contextElementParamMapperFactory", contextElementParamMapperFactory);

    pauseState.setToAddress("to1,to2");
    pauseState.setCcAddress("cc1,cc2");

    PortalConfig portalConfig = new PortalConfig();
    portalConfig.setUrl(BASE_URL);
    when(configuration.getPortal()).thenReturn(portalConfig);
    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn("baseUrl");

    User user = User.Builder.anUser().uuid(UUID).name(USERNAME).email(EMAIL).emailVerified(true).build();
    when(userServiceImpl.getUserWithAcceptedInviteByEmail(any(), any())).thenReturn(user);
  }

  /**
   * Should send email and return correlation id on execute.
   *
   * @throws EmailException    the email exception
   * @throws TemplateException the template exception
   * @throws IOException       the io exception
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldSendEmailAndReturnCorrelationIdOnExecute() throws EmailException, TemplateException, IOException {
    pauseState.setBody("body");
    pauseState.setSubject("subject");

    ExecutionResponse executionResponse = pauseState.execute(context);
    assertThat(executionResponse).extracting(ExecutionResponse::getExecutionStatus).isEqualTo(ExecutionStatus.PAUSED);
    assertThat(executionResponse.getStateExecutionData())
        .isInstanceOf(EmailStateExecutionData.class)
        .isEqualTo(expected.but().build());
    assertThat(executionResponse.getErrorMessage()).isNull();

    verify(emailNotificationService)
        .send(EmailData.builder()
                  .to(Lists.newArrayList("to1", "to2"))
                  .accountId(ACCOUNT_ID)
                  .cc(Lists.newArrayList("cc1", "cc2"))
                  .subject("subject")
                  .body("body")
                  .build());
  }
}
