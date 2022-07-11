/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.trigger;

import static io.harness.beans.WorkflowType.PIPELINE;
import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_ID;
import static software.wings.utils.WingsTestConstants.USER_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Pipeline;
import software.wings.beans.User;
import software.wings.beans.Variable.VariableBuilder;
import software.wings.beans.trigger.Trigger;
import software.wings.dl.GenericDbCache;
import software.wings.security.AppPermissionSummary;
import software.wings.security.PermissionAttribute;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.AuthServiceImpl;
import software.wings.service.impl.security.auth.DeploymentAuthHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.UserGroupService;
import software.wings.utils.JsonUtils;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class TriggerAuthService1Test extends WingsBaseTest {
  @Inject @InjectMocks TriggerService triggerService;
  @Mock PipelineService pipelineService;
  @Mock FeatureFlagService featureFlagService;
  @Inject @InjectMocks UserGroupService userGroupService;
  @Mock AppService appService;
  @Mock GenericDbCache genericDbCache;

  @Inject @InjectMocks TriggerAuthHandler triggerAuthHandler;
  @Inject @InjectMocks DeploymentAuthHandler deploymentAuthHandler;
  @Inject @InjectMocks AuthServiceImpl authService;
  @Mock EnvironmentService environmentService;

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testTriggerPerms() {
    try (UserThreadLocal.Guard ignore1 = UserThreadLocal.userGuard(getUser(PIPELINE_ID))) {
      when(featureFlagService.isEnabled(FeatureName.PIPELINE_PER_ENV_DEPLOYMENT_PERMISSION, ACCOUNT_ID))
          .thenReturn(true);
      Pipeline pipeline1 = JsonUtils.readResourceFile("triggers/pipeline.json", Pipeline.class);
      pipeline1.setUuid(PIPELINE_ID);
      pipeline1.getPipelineStages().get(0).getPipelineStageElements().get(0).getRuntimeInputsConfig().setTimeout(
          6000000L);
      pipeline1.setEnvParameterized(true);
      pipeline1.setEnvIds(Collections.singletonList("${env}"));
      pipeline1.setPipelineVariables(Collections.singletonList(
          VariableBuilder.aVariable().entityType(EntityType.ENVIRONMENT).name("${env}").build()));

      doReturn(Account.Builder.anAccount().build()).when(genericDbCache).get(any(), any());
      doReturn(ACCOUNT_ID).when(appService).getAccountIdByAppId(APP_ID);
      doReturn(pipeline1)
          .when(pipelineService)
          .readPipeline(eq(pipeline1.getAppId()), eq(pipeline1.getUuid()), eq(true));
      doReturn(pipeline1)
          .when(pipelineService)
          .readPipeline(eq(pipeline1.getAppId()), eq(pipeline1.getUuid()), eq(true));
      doReturn(pipeline1).when(pipelineService).getPipeline(eq(pipeline1.getAppId()), eq(pipeline1.getUuid()));
      doReturn(Environment.Builder.anEnvironment().build())
          .when(environmentService)
          .get(eq(pipeline1.getAppId()), any());
      on(authService).set("dbCache", genericDbCache);

      getUser(pipeline1.getUuid());

      Trigger trigger = Trigger.builder()
                            .pipelineId(pipeline1.getUuid())
                            .appId(pipeline1.getAppId())
                            .accountId(pipeline1.getAccountId())
                            .workflowVariables(Map.of("${env}", ENV_ID))
                            .build();
      assertThatCode(() -> triggerService.authorize(trigger, false)).doesNotThrowAnyException();

      Trigger trigger1 = Trigger.builder()
                             .pipelineId(pipeline1.getUuid())
                             .appId(pipeline1.getAppId())
                             .accountId(pipeline1.getAccountId())
                             .workflowVariables(Map.of("${env}", ENV_ID + "1"))
                             .build();
      assertThatThrownBy(() -> triggerService.authorize(trigger1, false));

      pipeline1.setUuid(PIPELINE_ID + "1");
      doReturn(pipeline1)
          .when(pipelineService)
          .readPipeline(eq(pipeline1.getAppId()), eq(pipeline1.getUuid()), eq(true));
      doReturn(pipeline1)
          .when(pipelineService)
          .readPipeline(eq(pipeline1.getAppId()), eq(pipeline1.getUuid()), eq(true));
      doReturn(pipeline1).when(pipelineService).getPipeline(eq(pipeline1.getAppId()), eq(pipeline1.getUuid()));

      Trigger trigger2 = Trigger.builder()
                             .pipelineId(pipeline1.getUuid())
                             .appId(pipeline1.getAppId())
                             .accountId(pipeline1.getAccountId())
                             .workflowVariables(Map.of("${env}", ENV_ID))
                             .build();
      assertThatThrownBy(() -> triggerService.authorize(trigger2, false));
    }
  }

  private User getUser(String pipelineId1) {
    User user = User.Builder.anUser().name(USER_NAME).uuid(USER_ID).build();
    user.setUserRequestContext(
        UserRequestContext.builder()
            .accountId(ACCOUNT_ID)
            .userPermissionInfo(
                UserPermissionInfo.builder()
                    .accountId(ACCOUNT_ID)
                    .appPermissionMapInternal(new HashMap<>() {
                      {
                        put(APP_ID,
                            AppPermissionSummary.builder()
                                .canCreateTemplate(true)
                                .deploymentPermissions(new HashMap<PermissionAttribute.Action, Set<String>>() {
                                  {
                                    put(PermissionAttribute.Action.EXECUTE_PIPELINE,
                                        new HashSet<>(singletonList(pipelineId1)));
                                    put(PermissionAttribute.Action.UPDATE, new HashSet<>(singletonList(pipelineId1)));
                                    put(PermissionAttribute.Action.CREATE, new HashSet<>(singletonList(pipelineId1)));
                                    put(PermissionAttribute.Action.DELETE, new HashSet<>(singletonList(pipelineId1)));
                                    put(PermissionAttribute.Action.READ, new HashSet<>(singletonList(pipelineId1)));
                                  }
                                })
                                .envExecutableElementDeployPermissions(
                                    Map.of(AppPermissionSummary.ExecutableElementInfo.builder()
                                               .entityId(pipelineId1)
                                               .entityType(PIPELINE.name())
                                               .build(),
                                        Collections.singleton(ENV_ID)))
                                .pipelineExecutePermissionsForEnvs(Collections.singleton(ENV_ID))
                                .build());
                      }
                    })
                    .build())
            .build());
    return user;
  }
}
