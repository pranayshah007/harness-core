/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.k8s.model.HelmVersion;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.settings.helm.HelmRepoConfigValidationTaskParams;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.service.intfc.security.EncryptionService;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class HelmRepoConfigValidationTaskTest extends CategoryTest {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String REPO_DISPLAY_NAME = "test-repo";
  private static final String HTTP_CHART_REPO_URL = "https://charts.harness.io";
  private static final String REPO_USERNAME = "test-username";
  private static final String REPO_PASSWORD = "secret-password";
  private static final String WORKING_DIRECTORY = "/root/not/usable/working/directory";

  private static final List<EncryptedDataDetail> ENCRYPTED_DATA_DETAILS =
      List.of(EncryptedDataDetail.builder().build());

  final DelegateTaskPackage delegateTaskPackage =
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build();

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private EncryptionService encryptionService;
  @Mock private HelmTaskHelper helmTaskHelper;

  @Mock private ILogStreamingTaskClient logStreamingTaskClient;

  // logStreamingTaskClient is null here actually, expecting @InjectMocks to do his magic
  @InjectMocks
  private HelmRepoConfigValidationTask validationTask =
      new HelmRepoConfigValidationTask(delegateTaskPackage, logStreamingTaskClient, ignore -> {}, () -> true);

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateRunTaskHttpHelm() {
    final HelmRepoConfigValidationTaskParams params = createHTTPTaskParams(true);

    doReturn("").when(helmTaskHelper).getCacheDir(anyString(), eq(true), any(HelmVersion.class));
    doReturn(WORKING_DIRECTORY).when(helmTaskHelper).createNewDirectoryAtPath(anyString());

    validationTask.run(params);

    ArgumentCaptor<String> repoNameCaptor = ArgumentCaptor.forClass(String.class);

    verify(helmTaskHelper).initHelm(eq(WORKING_DIRECTORY), eq(HelmVersion.V3), anyLong());
    verify(helmTaskHelper)
        .tryAddHelmRepo(repoNameCaptor.capture(), eq(REPO_DISPLAY_NAME), eq(HTTP_CHART_REPO_URL), eq(REPO_USERNAME),
            eq(REPO_PASSWORD.toCharArray()), anyString(), any(HelmVersion.class), anyLong(), eq(""), eq(null));
    verify(helmTaskHelper)
        .removeRepo(eq(repoNameCaptor.getValue()), eq(WORKING_DIRECTORY), any(HelmVersion.class), anyLong());

    assertThat(repoNameCaptor.getValue())
        .withFailMessage("We used to have a generated UUID v4 repo name. "
            + "If this changed then check the logic around helm repo remove, it shouldn't be called if the repo name is not generated each time")
        .hasSize(36);
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateRunTaskHttpHelmDisableCache() {
    final HelmRepoConfigValidationTaskParams params = createHTTPTaskParams(false);
    String cacheDirectory = Files.createTempDirectory("test-helm-chart").toString();

    doReturn(cacheDirectory).when(helmTaskHelper).getCacheDir(anyString(), eq(false), any(HelmVersion.class));
    doReturn(WORKING_DIRECTORY).when(helmTaskHelper).createNewDirectoryAtPath(anyString());

    validationTask.run(params);

    ArgumentCaptor<String> repoNameCaptor = ArgumentCaptor.forClass(String.class);

    verify(helmTaskHelper).initHelm(eq(WORKING_DIRECTORY), eq(HelmVersion.V3), anyLong());
    verify(helmTaskHelper)
        .tryAddHelmRepo(repoNameCaptor.capture(), eq(REPO_DISPLAY_NAME), eq(HTTP_CHART_REPO_URL), eq(REPO_USERNAME),
            eq(REPO_PASSWORD.toCharArray()), anyString(), any(HelmVersion.class), anyLong(), eq(cacheDirectory),
            eq(null));
    verify(helmTaskHelper)
        .removeRepo(eq(repoNameCaptor.getValue()), eq(WORKING_DIRECTORY), any(HelmVersion.class), anyLong());

    assertThat(new File(cacheDirectory)).doesNotExist();

    assertThat(repoNameCaptor.getValue())
        .withFailMessage("We used to have a generated UUID v4 repo name. "
            + "If this changed then check the logic around helm repo remove, it shouldn't be called if the repo name is not generated each time")
        .hasSize(36);
  }

  private HelmRepoConfigValidationTaskParams createHTTPTaskParams(boolean useCache) {
    return HelmRepoConfigValidationTaskParams.builder()
        .useCache(useCache)
        .repoDisplayName(REPO_DISPLAY_NAME)
        .helmRepoConfig(HttpHelmRepoConfig.builder()
                            .accountId(ACCOUNT_ID)
                            .chartRepoUrl(HTTP_CHART_REPO_URL)
                            .username(REPO_USERNAME)
                            .password(REPO_PASSWORD.toCharArray())
                            .build())
        .encryptedDataDetails(ENCRYPTED_DATA_DETAILS)
        .build();
  }
}