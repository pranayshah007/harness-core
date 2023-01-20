/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.DeleteResourcesType;
import io.harness.delegate.task.k8s.K8sDeleteRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class K8sDeleteBaseHandlerTest extends CategoryTest {
  @Mock K8sTaskHelperBase k8sTaskHelperBase;
  @Mock private LogCallback logCallback;
  @Mock private KubernetesConfig kubernetesConfig;
  @Mock K8sReleaseHandler releaseHandler;

  @InjectMocks private K8sDeleteBaseHandler k8sDeleteBaseHandler;
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(anyBoolean());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldGetResourceIdsToDeleteFromResourceName() throws Exception {
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ResourceName)
                                         .resources("Deployment/test-deployment, Service/test-service")
                                         .build();

    List<KubernetesResourceId> result =
        k8sDeleteBaseHandler.getResourceIdsToDelete(deleteRequest, kubernetesConfig, logCallback);
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(2);
    int deploymentKindFound = 0;
    int serviceKindFound = 0;
    for (KubernetesResourceId kubernetesResourceId : result) {
      if (kubernetesResourceId.getKind().equals("Deployment")
          && kubernetesResourceId.getName().equals("test-deployment")) {
        deploymentKindFound = 1;
      }
      if (kubernetesResourceId.getKind().equals("Service") && kubernetesResourceId.getName().equals("test-service")) {
        serviceKindFound = 1;
      }
    }
    assertThat(deploymentKindFound).isEqualTo(1);
    assertThat(serviceKindFound).isEqualTo(1);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldGetResourceIdsToDeleteFromResourceNameWithNamespace() throws Exception {
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ResourceName)
                                         .resources("test/Deployment/test-deployment, test/Service/test-service")
                                         .build();

    List<KubernetesResourceId> result =
        k8sDeleteBaseHandler.getResourceIdsToDelete(deleteRequest, kubernetesConfig, logCallback);
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(2);
    int deploymentKindFound = 0;
    int serviceKindFound = 0;
    for (KubernetesResourceId kubernetesResourceId : result) {
      if (kubernetesResourceId.getKind().equals("Deployment")
          && kubernetesResourceId.getName().equals("test-deployment")
          && kubernetesResourceId.getNamespace().equals("test")) {
        deploymentKindFound = 1;
      }
      if (kubernetesResourceId.getKind().equals("Service") && kubernetesResourceId.getName().equals("test-service")
          && kubernetesResourceId.getNamespace().equals("test")) {
        serviceKindFound = 1;
      }
    }
    assertThat(deploymentKindFound).isEqualTo(1);
    assertThat(serviceKindFound).isEqualTo(1);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldGetEmptyResourceIdsToDeleteFromResourceNameAreEmpty() throws Exception {
    K8sDeleteRequest deleteRequest =
        K8sDeleteRequest.builder().deleteResourcesType(DeleteResourcesType.ResourceName).resources("").build();

    List<KubernetesResourceId> result =
        k8sDeleteBaseHandler.getResourceIdsToDelete(deleteRequest, kubernetesConfig, logCallback);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testFailGetResourceIdsToDeleteFromResourceNameWithBadResources() {
    K8sDeleteRequest deleteRequest =
        K8sDeleteRequest.builder()
            .deleteResourcesType(DeleteResourcesType.ResourceName)
            .resources("bad/test/Deployment/test-deployment, bad/test/Service/test-service")
            .build();

    try {
      k8sDeleteBaseHandler.getResourceIdsToDelete(deleteRequest, kubernetesConfig, logCallback);
      fail("Should throw exception if resourcename is in bad format");
    } catch (Exception exception) {
      assertThat(exception).isInstanceOf(WingsException.class);
      assertThat(exception.getMessage())
          .isEqualTo(
              "Invalid Kubernetes resource name bad/test/Deployment/test-deployment. Should be in format Kind/Name");
    }
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testFailGetResourceIdsToDeleteFromResourceNameWithStar() {
    K8sDeleteRequest deleteRequest =
        K8sDeleteRequest.builder().deleteResourcesType(DeleteResourcesType.ResourceName).resources("*").build();

    try {
      k8sDeleteBaseHandler.getResourceIdsToDelete(deleteRequest, kubernetesConfig, logCallback);
      fail("Should throw exception if ResourceName has *");
    } catch (Exception exception) {
      assertThat(exception).isInstanceOf(InvalidArgumentsException.class);
      assertThat(exception.getMessage()).isEqualTo("Invalid resource name. Use release name instead.");
    }
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldGetResourceIdsToDeleteFromReleaseName() throws Exception {
    String releaseName = "test-release";
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ReleaseName)
                                         .releaseName(releaseName)
                                         .deleteNamespacesForRelease(false)
                                         .useDeclarativeRollback(true)
                                         .build();

    List<KubernetesResourceId> kubernetesResourcesWithoutNamespace =
        Arrays.asList(KubernetesResourceId.builder().kind("Deployment").name("test-deployment").build(),
            KubernetesResourceId.builder().kind("Service").name("test-service").build());

    when(k8sTaskHelperBase.getResourceIdsForDeletion(true, releaseName, kubernetesConfig, logCallback, false))
        .thenReturn(kubernetesResourcesWithoutNamespace);

    List<KubernetesResourceId> result =
        k8sDeleteBaseHandler.getResourceIdsToDelete(deleteRequest, kubernetesConfig, logCallback);
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(2);
    assertThat(result).containsAll(kubernetesResourcesWithoutNamespace);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldGetResourceIdsToDeleteFromReleaseNameAndNamespace() throws Exception {
    String releaseName = "test-release";
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ReleaseName)
                                         .releaseName(releaseName)
                                         .deleteNamespacesForRelease(true)
                                         .useDeclarativeRollback(true)
                                         .build();

    List<KubernetesResourceId> kubernetesResources =
        Arrays.asList(KubernetesResourceId.builder().kind("Deployment").name("test-deployment").build(),
            KubernetesResourceId.builder().kind("Service").name("test-service").build(),
            KubernetesResourceId.builder().kind("Namespace").name("test").build());

    when(k8sTaskHelperBase.getResourceIdsForDeletion(true, releaseName, kubernetesConfig, logCallback, true))
        .thenReturn(kubernetesResources);

    List<KubernetesResourceId> result =
        k8sDeleteBaseHandler.getResourceIdsToDelete(deleteRequest, kubernetesConfig, logCallback);
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(3);
    assertThat(result).containsAll(kubernetesResources);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldGetEmptyResourceIdsToDeleteIfNoResourcesFoundInRelease() throws Exception {
    String releaseName = "test-release";
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ReleaseName)
                                         .releaseName(releaseName)
                                         .deleteNamespacesForRelease(true)
                                         .build();

    List<KubernetesResourceId> kubernetesResources = Collections.emptyList();

    when(k8sTaskHelperBase.getResourceIdsForDeletion(true, releaseName, kubernetesConfig, logCallback, true))
        .thenReturn(kubernetesResources);

    List<KubernetesResourceId> result =
        k8sDeleteBaseHandler.getResourceIdsToDelete(deleteRequest, kubernetesConfig, logCallback);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldThrowExceptionIfManifestPathIsUsed() throws Exception {
    String releaseName = "test-release";
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ManifestPath)
                                         .resources("anything")
                                         .releaseName(releaseName)
                                         .deleteNamespacesForRelease(true)
                                         .build();

    try {
      k8sDeleteBaseHandler.getResourceIdsToDelete(deleteRequest, kubernetesConfig, logCallback);
      fail("Should throw exception if manifest path is used");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(UnsupportedOperationException.class);
      assertThat(e.getMessage()).isEqualTo("Delete resource type: [ManifestPath]");
    }
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldReturnSuccessResponse() {
    K8sDeployResponse response = k8sDeleteBaseHandler.getSuccessResponse();
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
  }
}
