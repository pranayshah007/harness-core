/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.kustomize;

import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import io.harness.CategoryTest;
import io.harness.beans.FileData;
import io.harness.category.element.UnitTests;
import io.harness.cli.CliResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.kustomize.KustomizeClient;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class KustomizeTaskHelperTest extends CategoryTest {
  @Mock KustomizeClient kustomizeClient;
  @Mock LogCallback logCallback;

  @InjectMocks KustomizeTaskHelper kustomizeTaskHelper;
  KustomizeTaskHelper spyKustomizeTaskHelper = spy(new KustomizeTaskHelper());

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testBuild() throws InterruptedException, TimeoutException, IOException {
    shouldCallClientBuild();
    shouldCallClientBuildWithPlugins();
    shouldHandleTimeoutException();
    shouldHandleIOException();
    shouldHandleInterrupedException();
    shouldHandleClientBuildFailure();
    testClientBuildFailureWithNoOutput();
  }

  private void shouldHandleClientBuildFailure() throws InterruptedException, IOException, TimeoutException {
    final String RANDOM = "RANDOM";
    CliResponse cliResponse =
        CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).output(RANDOM).build();
    doReturn(cliResponse).when(kustomizeClient).build(RANDOM, RANDOM, RANDOM, logCallback);

    assertThatThrownBy(() -> kustomizeTaskHelper.build(RANDOM, RANDOM, null, RANDOM, logCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Kustomize build failed. Msg: " + RANDOM);
  }

  private void testClientBuildFailureWithNoOutput() throws InterruptedException, IOException, TimeoutException {
    final String RANDOM = "RANDOM";
    CliResponse cliResponse = CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();
    doReturn(cliResponse).when(kustomizeClient).build(RANDOM, RANDOM, RANDOM, logCallback);

    assertThatThrownBy(() -> kustomizeTaskHelper.build(RANDOM, RANDOM, null, RANDOM, logCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Kustomize build failed.");
  }

  private void shouldHandleInterrupedException() throws InterruptedException, IOException, TimeoutException {
    final String RANDOM = "RANDOM";
    doThrow(InterruptedException.class).when(kustomizeClient).build(RANDOM, RANDOM, RANDOM, logCallback);

    assertThatThrownBy(() -> kustomizeTaskHelper.build(RANDOM, RANDOM, null, RANDOM, logCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Kustomize build interrupted");
  }

  private void shouldHandleIOException() throws InterruptedException, IOException, TimeoutException {
    final String RANDOM = "RANDOM";
    doThrow(IOException.class).when(kustomizeClient).build(RANDOM, RANDOM, RANDOM, logCallback);

    assertThatThrownBy(() -> kustomizeTaskHelper.build(RANDOM, RANDOM, null, RANDOM, logCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("IO Failure occurred while running kustomize build");
  }

  private void shouldHandleTimeoutException() throws InterruptedException, IOException, TimeoutException {
    final String RANDOM = "RANDOM";
    doThrow(TimeoutException.class).when(kustomizeClient).build(RANDOM, RANDOM, RANDOM, logCallback);

    assertThatThrownBy(() -> kustomizeTaskHelper.build(RANDOM, RANDOM, null, RANDOM, logCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Kustomize build timed out");
  }

  private void shouldCallClientBuildWithPlugins() throws InterruptedException, IOException, TimeoutException {
    final String RANDOM = "RANDOM";
    CliResponse cliResponse =
        CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).output(RANDOM).build();
    doReturn(cliResponse).when(kustomizeClient).buildWithPlugins(RANDOM, RANDOM, RANDOM, RANDOM, logCallback);

    List<FileData> manifestFiles = kustomizeTaskHelper.build(RANDOM, RANDOM, RANDOM, RANDOM, logCallback);
    assertThat(manifestFiles).hasSize(1);
    assertThat(manifestFiles.get(0).getFileContent()).isEqualTo(RANDOM);
  }

  private void shouldCallClientBuild() throws InterruptedException, IOException, TimeoutException {
    final String RANDOM = "RANDOM";
    CliResponse cliResponse =
        CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).output(RANDOM).build();
    doReturn(cliResponse).when(kustomizeClient).build(RANDOM, RANDOM, RANDOM, logCallback);

    List<FileData> manifestFiles = kustomizeTaskHelper.build(RANDOM, RANDOM, null, RANDOM, logCallback);
    assertThat(manifestFiles).hasSize(1);
    assertThat(manifestFiles.get(0).getFileContent()).isEqualTo(RANDOM);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testKustomizeBuildForApply() {
    applyFilesSizeShouldBeOne();
    shouldCallKustomizeBuild();
  }

  private void shouldCallKustomizeBuild() {
    String RANDOM = "RANDOM";
    List<String> file = Collections.singletonList("file");
    FileData manifestFile = FileData.builder().build();
    List<FileData> manifestFiles = Collections.singletonList(manifestFile);
    ArgumentCaptor<String> pluginRootCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> kustomizeDirPathCaptor = ArgumentCaptor.forClass(String.class);
    doReturn(manifestFiles)
        .when(spyKustomizeTaskHelper)
        .build(eq(RANDOM), eq(RANDOM), pluginRootCaptor.capture(), kustomizeDirPathCaptor.capture(), eq(logCallback));

    List<FileData> actualManifestFiles =
        spyKustomizeTaskHelper.buildForApply(RANDOM, RANDOM, RANDOM, file, Collections.emptyList(), logCallback);

    assertThat(actualManifestFiles).isEqualTo(manifestFiles);
    assertThat(kustomizeDirPathCaptor.getValue()).isEqualTo("file");
    assertThat(pluginRootCaptor.getValue()).isEqualTo(RANDOM);
  }

  private void applyFilesSizeShouldBeOne() {
    assertThatThrownBy(() -> kustomizeTaskHelper.buildForApply(null, null, null, null, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Apply files can't be empty");

    assertThatThrownBy(() -> kustomizeTaskHelper.buildForApply(null, null, null, Collections.emptyList(), null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Apply files can't be empty");

    List<String> applyFiles = Arrays.asList("file1", "file2");

    assertThatThrownBy(() -> kustomizeTaskHelper.buildForApply(null, null, null, applyFiles, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Apply with Kustomize is supported for single file only");
  }
}
