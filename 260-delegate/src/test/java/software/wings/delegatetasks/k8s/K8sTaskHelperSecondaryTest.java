/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.clienttools.ClientTool;
import io.harness.delegate.clienttools.InstallUtils;
import io.harness.delegate.task.git.ScmFetchFilesHelperNG;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.kubectl.Utils;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesResource;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.command.ExecutionLogCallback;

import com.google.api.client.util.Charsets;
import com.google.common.io.Resources;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.powermock.api.mockito.PowerMockito;
import org.zeroturnaround.exec.ProcessOutput;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.StartedProcess;

/*
 * Put All tests that use powermock here
 */

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sTaskHelperSecondaryTest extends WingsBaseTest {
  @Mock private Process process;
  @Mock private StartedProcess startedProcess;
  @Mock private ExecutionLogCallback executionLogCallback;
  @Mock private ScmFetchFilesHelperNG scmFetchFilesHelper;
  @InjectMocks private K8sTaskHelperBase k8sTaskHelperBase;
  private static MockedStatic<Utils> mockUtil;

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGenerateTruncatedFileListForLogging() throws Exception {
    mockStatic(Files.class);
    when(Files.isRegularFile(any(Path.class))).thenReturn(true);
    MockPath basePath = new MockPath("foo");
    String loggedFiles;

    loggedFiles =
        k8sTaskHelperBase.generateTruncatedFileListForLogging(basePath, getNFilePathsWithSuffix(0, "file").stream());
    assertThat(loggedFiles).isEmpty();
    assertThat(loggedFiles).doesNotContain("..more");

    loggedFiles =
        k8sTaskHelperBase.generateTruncatedFileListForLogging(basePath, getNFilePathsWithSuffix(1, "file").stream());
    assertThat(loggedFiles).isNotEmpty();
    assertThat(loggedFiles).doesNotContain("..more");

    loggedFiles =
        k8sTaskHelperBase.generateTruncatedFileListForLogging(basePath, getNFilePathsWithSuffix(100, "file").stream());
    assertThat(loggedFiles).isNotEmpty();
    assertThat(loggedFiles).doesNotContain("..more");

    loggedFiles =
        k8sTaskHelperBase.generateTruncatedFileListForLogging(basePath, getNFilePathsWithSuffix(101, "file").stream());
    assertThat(loggedFiles).isNotEmpty();
    assertThat(loggedFiles).contains("..1 more");

    loggedFiles =
        k8sTaskHelperBase.generateTruncatedFileListForLogging(basePath, getNFilePathsWithSuffix(199, "file").stream());
    assertThat(loggedFiles).isNotEmpty();
    assertThat(loggedFiles).contains("..99 more");
  }

  private List<Path> getNFilePathsWithSuffix(int n, String suffix) {
    List<Path> paths = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      Path mockPath = mock(Path.class);
      paths.add(mockPath);
      when(mockPath.relativize(any(Path.class))).thenAnswer(invocationOnMock -> new MockPath("foo"));
    }
    return paths;
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDoStatusCheckForDeployment() throws Exception {
    setupForDoStatusCheckForAllResources();

    doStatusCheck("/k8s/deployment.yaml",
        "kubectl --kubeconfig=config-path rollout status Deployment/nginx-deployment --watch=true", false);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDoStatusCheckForAllResourcesForDeployment() throws Exception {
    setupForDoStatusCheckForAllResources();

    doStatusCheck("/k8s/deployment.yaml",
        "kubectl --kubeconfig=config-path rollout status Deployment/nginx-deployment --watch=true", true);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDoStatusCheckForDC() throws Exception {
    setupForDoStatusCheckForAllResources();

    doStatusCheck("/k8s/deployment-config.yaml",
        "oc --kubeconfig=config-path rollout status DeploymentConfig/test-dc --namespace=default --watch=true", false);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDoStatusCheckForAllResourcesForDC() throws Exception {
    setupForDoStatusCheckForAllResources();

    doStatusCheck("/k8s/deployment-config.yaml",
        "oc --kubeconfig=config-path rollout status DeploymentConfig/test-dc --namespace=default --watch=true", true);
  }

  private void setupForDoStatusCheckForAllResources() throws Exception {
    mockUtil = mockStatic(Utils.class);
    ProcessResult processResult = new ProcessResult(0, new ProcessOutput(" ".getBytes()));
    PowerMockito.when(Utils.executeScript(anyString(), anyString(), any(), any(), any())).thenReturn(processResult);

    PowerMockito.when(Utils.encloseWithQuotesIfNeeded("kubectl")).thenReturn("kubectl");
    PowerMockito.when(Utils.encloseWithQuotesIfNeeded("oc")).thenReturn("oc");
    PowerMockito.when(Utils.encloseWithQuotesIfNeeded("config-path")).thenReturn("config-path");

    when(process.destroyForcibly()).thenReturn(process);
    PowerMockito.when(Utils.startScript(any(), any(), any(), any(), any())).thenReturn(startedProcess);
    when(startedProcess.getProcess()).thenReturn(process);
  }

  private void doStatusCheck(String manifestFilePath, String expectedOutput, boolean allResources) throws Exception {
    URL url = this.getClass().getResource(manifestFilePath);
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = ManifestHelper.processYaml(fileContents).get(0);

    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .kubectlPath("kubectl")
                                                      .ocPath("oc")
                                                      .kubeconfigPath("config-path")
                                                      .workingDirectory("working-dir")
                                                      .build();
    Kubectl client = Kubectl.client("kubectl", "config-path");

    MockedStatic<InstallUtils> mock = mockStatic(InstallUtils.class);
    PowerMockito.when(InstallUtils.getLatestVersionPath(ClientTool.OC)).thenReturn("oc");
    if (allResources) {
      k8sTaskHelperBase.doStatusCheckForAllResources(client, Arrays.asList(resource.getResourceId()),
          k8sDelegateTaskParams, "default", executionLogCallback, true);
    } else {
      k8sTaskHelperBase.doStatusCheck(client, resource.getResourceId(), k8sDelegateTaskParams, executionLogCallback);
    }
    mock.close();

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    PowerMockito.verifyStatic(Utils.class);
    Utils.executeScript(any(), captor.capture(), any(), any(), any());
    assertThat(captor.getValue()).isEqualTo(expectedOutput);
    mockUtil.close();
  }
}
