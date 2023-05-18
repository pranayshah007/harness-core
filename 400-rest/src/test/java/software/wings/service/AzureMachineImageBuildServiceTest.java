/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AzureImageDefinition;
import software.wings.beans.AzureImageVersion;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.helpers.ext.azure.AzureDelegateHelperService;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.AzureMachineImageBuildServiceImpl;
import software.wings.service.intfc.AzureMachineImageBuildService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class AzureMachineImageBuildServiceTest extends WingsBaseTest {
  @Mock AzureDelegateHelperService azureDelegateHelperService;
  @Inject @InjectMocks AzureMachineImageBuildService buildService = new AzureMachineImageBuildServiceImpl();

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testGetBuilds() {
    when(azureDelegateHelperService.listImageDefinitionVersions(
             any(), any(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Lists.newArrayList(AzureImageVersion.builder()
                                           .name("v1.0")
                                           .imageDefinitionName("imageDefinition")
                                           .location("location")
                                           .subscriptionId("subscriptionId")
                                           .resourceGroupName("resourceGroupName")
                                           .galleryName("galleryName")
                                           .build()));
    ArtifactStreamAttributes attributes = ArtifactStreamAttributes.builder()
                                              .artifactStreamType("AZURE_MACHINE_IMAGE")
                                              .osType("LINUX")
                                              .azureResourceGroup("resourceGroup")
                                              .subscriptionId("subscriptionId")
                                              .imageType("IMAGE_GALLERY")
                                              .azureImageGalleryName("galleryName")
                                              .azureImageDefinition("imageDefinition")
                                              .build();
    List<BuildDetails> buildDetails = buildService.getBuilds("appId", attributes, null, null);

    Map<String, String> metadata = new HashMap<>();
    metadata.put("galleryName", "galleryName");
    metadata.put("osType", "LINUX");
    metadata.put("resourceGroup", "resourceGroup");
    metadata.put("subscriptionId", "subscriptionId");
    metadata.put("imageDefinitionName", "imageDefinition");
    metadata.put("imageType", "IMAGE_GALLERY");

    assertEquals("Should return one build", 1, buildDetails.size());

    // Not using equals method as equals only checks for revision & number fields to be equal.
    // It is important that we check that all fields are set correctly.
    // We are converting the objects to JsonNode and applying equals check.
    ObjectMapper mapper = new ObjectMapper();
    JsonNode expected = mapper.convertValue(Lists.newArrayList(BuildDetails.Builder.aBuildDetails()
                                                                   .withMetadata(metadata)
                                                                   .withNumber("v1.0")
                                                                   .withRevision("v1.0")
                                                                   .withUiDisplayName("Image: v1.0")
                                                                   .build()),
        JsonNode.class);
    assertEquals("Build details should match", expected, mapper.convertValue(buildDetails, JsonNode.class));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testValidateThenInferAttribute() {
    ArtifactStreamAttributes attributes = ArtifactStreamAttributes.builder()
                                              .artifactStreamType("AZURE_MACHINE_IMAGE")
                                              .osType("LINUX")
                                              .azureResourceGroup("resourceGroup")
                                              .subscriptionId("subscriptionId")
                                              .imageType("IMAGE_GALLERY")
                                              .azureImageGalleryName("galleryName")
                                              .azureImageDefinition("imageDefinition")
                                              .build();
    when(azureDelegateHelperService.listImageDefinitions(
             any(), any(), eq("subscriptionId"), eq("resourceGroup"), eq("galleryName")))
        .thenReturn(Lists.newArrayList(AzureImageDefinition.builder().name("imageDefinition").osType("LINUX").build()));

    ArtifactStreamAttributes expected = attributes.toBuilder().osType("LINUX").build();
    ArtifactStreamAttributes value = buildService.validateThenInferAttributes(null, null, attributes);
    assertEquals("Inferred values are updated", expected, value);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testWhenDefinitionDoesNotExistWhenInfer() {
    ArtifactStreamAttributes attributes = ArtifactStreamAttributes.builder()
                                              .artifactStreamType("AZURE_MACHINE_IMAGE")
                                              .osType("LINUX")
                                              .azureResourceGroup("resourceGroup")
                                              .subscriptionId("subscriptionId")
                                              .imageType("IMAGE_GALLERY")
                                              .azureImageGalleryName("galleryName")
                                              .azureImageDefinition("imageDefinition")
                                              .build();
    when(azureDelegateHelperService.listImageDefinitions(
             any(), any(), eq("subscriptionId"), eq("resourceGroup"), eq("galleryName")))
        .thenReturn(
            Lists.newArrayList(AzureImageDefinition.builder().name("randomImageDefinition").osType("LINUX").build()));
    assertThatThrownBy(() -> buildService.validateThenInferAttributes(null, null, attributes))
        .isInstanceOf(InvalidArgumentsException.class);
  }
}
