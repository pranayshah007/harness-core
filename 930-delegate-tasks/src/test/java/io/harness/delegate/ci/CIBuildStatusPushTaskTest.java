/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ci;

import static io.harness.rule.OwnerRule.HARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.status.BuildStatusPushResponse;
import io.harness.delegate.beans.ci.status.BuildStatusPushResponse.Status;
import io.harness.delegate.task.ci.CIBuildStatusPushParameters;
import io.harness.delegate.task.ci.CIBuildStatusPushTask;
import io.harness.delegate.task.ci.GitSCMType;
import io.harness.git.checks.GitStatusCheckHelper;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CIBuildStatusPushTaskTest extends CategoryTest {
  @Mock GitStatusCheckHelper gitStatusCheckHelper;

  private final String APP_ID = "APP_ID";
  private final String DESC = "desc";
  private final String STATE = "success";
  private final String BUILD_NUMBER = "buildNumber";
  private final String TITLE = "title";
  private final String REPO = "repo";
  private final String OWNER = "owner";
  private final String SHA = "e9a0d31c5ac677ec1e06fb3ab69cd1d2cc62a74a";
  private final String IDENTIFIER = "stageIdentifier";
  private final String INSTALL_ID = "123";
  private final String TARGET_URL = "https://app.harness.io";
  private final String KEY = "dummyKey";

  @InjectMocks
  private CIBuildStatusPushTask ciBuildStatusPushTask = new CIBuildStatusPushTask(
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);

  @Before
  public void setUp() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testDelegatePushStatusSuccessForGithub() throws IOException {
    when(gitStatusCheckHelper.sendStatus(any())).thenReturn(true);

    BuildStatusPushResponse buildStatusPushResponse =
        (BuildStatusPushResponse) ciBuildStatusPushTask.run(CIBuildStatusPushParameters.builder()
                                                                .appId(APP_ID)
                                                                .sha(SHA)
                                                                .key(KEY)
                                                                .identifier(IDENTIFIER)
                                                                .buildNumber(BUILD_NUMBER)
                                                                .installId(INSTALL_ID)
                                                                .owner(OWNER)
                                                                .repo(REPO)
                                                                .gitSCMType(GitSCMType.GITHUB)
                                                                .state(STATE)
                                                                .title(TITLE)
                                                                .target_url(TARGET_URL)
                                                                .desc(DESC)
                                                                .build());

    assertThat(buildStatusPushResponse.getStatus()).isEqualTo(Status.SUCCESS);
  }
}
