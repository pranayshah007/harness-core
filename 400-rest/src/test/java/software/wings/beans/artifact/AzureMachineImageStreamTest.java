/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.artifact;

import static io.harness.delegate.beans.azure.AzureMachineImageArtifactDTO.ImageType.IMAGE_GALLERY;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;

import static software.wings.beans.artifact.AzureMachineImageArtifactStream.ImageDefinition;
import static software.wings.beans.artifact.AzureMachineImageArtifactStream.OSType.LINUX;
import static software.wings.beans.artifact.AzureMachineImageArtifactStream.builder;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AzureMachineImageStreamTest extends CategoryTest {
  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testDisplayName() {
    ArtifactStream stream = builder().sourceName("definitionName").imageType(IMAGE_GALLERY).build();
    assertEquals("Display name should be equal", "definitionName_v1.0.0", stream.fetchArtifactDisplayName("v1.0.0"));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testShouldValidateReturnTrue() {
    ArtifactStream stream = builder().build();
    assertTrue("Should validate always returns true", stream.shouldValidate());
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testValidateImageTypeThrowsException() {
    ArtifactStream stream = builder().build();
    Assertions.assertThatThrownBy(stream::validateRequiredFields).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testValidateSubscriptionIdThrowsException() {
    ArtifactStream stream = builder().imageType(IMAGE_GALLERY).build();
    Assertions.assertThatThrownBy(stream::validateRequiredFields).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testValidateImageDefinitionThrowsException() {
    ArtifactStream stream = builder().imageType(IMAGE_GALLERY).subscriptionId("randomId").imageDefinition(null).build();
    Assertions.assertThatThrownBy(stream::validateRequiredFields).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testValidateImageFieldsDefinitionThrowsException() {
    AzureMachineImageArtifactStream stream = builder()
                                                 .imageType(IMAGE_GALLERY)
                                                 .subscriptionId("randomId")
                                                 .imageDefinition(ImageDefinition.builder().build())
                                                 .build();
    Assertions.assertThatThrownBy(stream::validateRequiredFields)
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid value(s) for Resource group or Gallery or Image definition");

    stream.getImageDefinition().setResourceGroup("resourceGroup");
    Assertions.assertThatThrownBy(stream::validateRequiredFields)
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid value(s) for Resource group or Gallery or Image definition");

    stream.getImageDefinition().setImageGalleryName("galleryName");
    Assertions.assertThatThrownBy(stream::validateRequiredFields)
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid value(s) for Resource group or Gallery or Image definition");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testNoValidationFailures() {
    AzureMachineImageArtifactStream stream = builder()
                                                 .imageType(IMAGE_GALLERY)
                                                 .subscriptionId("randomId")
                                                 .imageDefinition(ImageDefinition.builder()
                                                                      .resourceGroup("resourceGroup")
                                                                      .imageGalleryName("galleryName")
                                                                      .imageDefinitionName("definitionName")
                                                                      .build())
                                                 .build();
    stream.validateRequiredFields();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testInferProperties() {
    ArtifactStreamAttributes attributes = ArtifactStreamAttributes.builder().osType("LINUX").build();
    AzureMachineImageArtifactStream stream = AzureMachineImageArtifactStream.builder().build();
    stream.inferProperties(attributes);
    assertEquals("Should infer osType from attributes", LINUX, stream.getOsType());
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testAttributesGeneration() {
    ArtifactStream stream = builder()
                                .imageType(IMAGE_GALLERY)
                                .subscriptionId("subscriptionId")
                                .osType(LINUX)
                                .imageDefinition(ImageDefinition.builder()
                                                     .resourceGroup("resourceGroup")
                                                     .imageGalleryName("galleryName")
                                                     .imageDefinitionName("imageDefinitionName")
                                                     .build())
                                .build();
    stream.setArtifactStreamType("AZURE_MACHINE_IMAGE");
    assertEquals("ArtifactStreamAttributes should be generated correctly",
        ArtifactStreamAttributes.builder()
            .artifactStreamType("AZURE_MACHINE_IMAGE")
            .subscriptionId("subscriptionId")
            .osType("LINUX")
            .imageType("IMAGE_GALLERY")
            .azureImageDefinition("imageDefinitionName")
            .azureImageGalleryName("galleryName")
            .azureResourceGroup("resourceGroup")
            .build(),
        stream.fetchArtifactStreamAttributes(null));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testSourceNameGeneration() {
    ArtifactStream stream =
        builder()
            .imageType(IMAGE_GALLERY)
            .imageDefinition(ImageDefinition.builder().imageDefinitionName("definitionName").build())
            .build();
    assertEquals("Source Name should be correctly generated", "definitionName", stream.generateSourceName());
  }
}
