/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.model.ServiceHookContext.GOOGLE_APPLICATION_CREDENTIALS;
import static io.harness.k8s.model.ServiceHookContext.KUBE_CONFIG;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.utils.ServiceHookDTO;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.ServiceHookAction;
import io.harness.k8s.model.ServiceHookType;
import io.harness.rule.Owner;

import software.wings.beans.ServiceHookDelegateConfig;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class ServiceHookHandlerTest extends CategoryTest {
  @Inject ServiceHookHandler serviceHookHandler;

  @Before
  public void setup() {
    ServiceHookDelegateConfig serviceHookDelegateConfig =
        ServiceHookDelegateConfig.builder()
            .serviceHookActions(Collections.singletonList(ServiceHookAction.FETCH_FILES.getActionName()))
            .hookType(ServiceHookType.PRE_HOOK.getName())
            .content("echo \"helllo\"")
            .identifier("Sample Hook")
            .build();
    List<ServiceHookDelegateConfig> hooks = Collections.singletonList(serviceHookDelegateConfig);
    ServiceHookDTO serviceHookDTO = new ServiceHookDTO(K8sDelegateTaskParams.builder()
                                                           .kubectlPath("kubectl/path/sample")
                                                           .kubeconfigPath("kube/config/sample/path")
                                                           .gcpKeyFilePath("sample/gcp/key/path")
                                                           .build());
    serviceHookHandler = new ServiceHookHandler(hooks, serviceHookDTO, 5);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testRequiredHooksFunction() {
    List<ServiceHookDelegateConfig> test1 = serviceHookHandler.requiredHooks(
        ServiceHookAction.FETCH_FILES.getActionName(), ServiceHookType.PRE_HOOK.getName());
    List<ServiceHookDelegateConfig> test2 = serviceHookHandler.requiredHooks(
        ServiceHookAction.STEADY_STATE_CHECK.getActionName(), ServiceHookType.PRE_HOOK.getName());
    List<ServiceHookDelegateConfig> test3 = serviceHookHandler.requiredHooks(
        ServiceHookAction.FETCH_FILES.getActionName(), ServiceHookType.POST_HOOK.getName());
    assertThat(test1.size()).isEqualTo(1);
    assertThat(test2.size()).isEqualTo(0);
    assertThat(test3.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testContext() {
    assertThat(serviceHookHandler.getContext().get(GOOGLE_APPLICATION_CREDENTIALS.getContextName()))
        .isEqualTo("sample/gcp/key/path");
    assertThat(serviceHookHandler.getContext().get(KUBE_CONFIG.getContextName())).isEqualTo("kube/config/sample/path");
    assertThat(serviceHookHandler.getContext().get("PATH")).contains("kubectl/path");
  }
}
