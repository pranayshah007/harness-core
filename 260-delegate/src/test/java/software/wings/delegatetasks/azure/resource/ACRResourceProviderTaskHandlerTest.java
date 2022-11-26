/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.resource;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureContainerRegistryClient;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.resource.operation.acr.ACRListRepositoryTagsOperation;
import io.harness.delegate.task.azure.resource.operation.acr.ACRListRepositoryTagsOperationResponse;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.delegatetasks.azure.resource.taskhandler.ACRResourceProviderTaskHandler;

import com.azure.resourcemanager.containerregistry.models.Registry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ACRResourceProviderTaskHandlerTest extends WingsBaseTest {
  @Mock private AzureContainerRegistryClient azureContainerRegistryClient;

  @Spy @InjectMocks private ACRResourceProviderTaskHandler taskHandler;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTask() {
    String subscriptionId = "subscriptionId";
    String registryName = "registryName";
    String repositoryName = "repositoryName";
    String loginServerUrl = "loginServerUrl";
    AzureConfig azureConfig = AzureConfig.builder().build();
    ACRListRepositoryTagsOperation acrListRepositoryTagsOperation = ACRListRepositoryTagsOperation.builder()
                                                                        .subscriptionId(subscriptionId)
                                                                        .registryName(registryName)
                                                                        .repositoryName(repositoryName)
                                                                        .build();

    Registry registry = mock(Registry.class);

    doReturn(registryName).when(registry).name();
    doReturn(Collections.singletonList(registry))
        .when(azureContainerRegistryClient)
        .listContainerRegistries(azureConfig, subscriptionId);

    doReturn(loginServerUrl).when(registry).loginServerUrl();
    doReturn(Arrays.asList("tagOne", "tagTwo"))
        .when(azureContainerRegistryClient)
        .listRepositoryTags(azureConfig, loginServerUrl, repositoryName);

    ACRListRepositoryTagsOperationResponse acrListRepositoryTagsOperationResponse =
        (ACRListRepositoryTagsOperationResponse) taskHandler.executeTask(acrListRepositoryTagsOperation, azureConfig);

    List<String> repositoryTags = acrListRepositoryTagsOperationResponse.getRepositoryTags();

    assertThat(repositoryTags).isNotEmpty();
    assertThat(repositoryTags).size().isEqualTo(2);
    String tagOne = repositoryTags.get(0);
    assertThat(tagOne).isEqualTo("tagOne");
    String tagTwo = repositoryTags.get(1);
    assertThat(tagTwo).isEqualTo("tagTwo");
  }
}
