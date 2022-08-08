/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.connector.utils;

import io.harness.utils.RequestField;

import software.wings.graphql.schema.mutation.connector.input.docker.QLDockerConnectorInput;
import software.wings.graphql.schema.mutation.connector.input.docker.QLDockerConnectorInput.QLDockerConnectorInputBuilder;
import software.wings.graphql.schema.mutation.connector.input.git.QLGitConnectorInput;
import software.wings.graphql.schema.mutation.connector.input.git.QLGitConnectorInput.QLGitConnectorInputBuilder;
import software.wings.graphql.schema.mutation.connector.input.git.QLUpdateGitConnectorInput;
import software.wings.graphql.schema.mutation.connector.input.git.QLUpdateGitConnectorInput.QLUpdateGitConnectorInputBuilder;
import software.wings.graphql.schema.mutation.connector.input.helm.QLAmazonS3PlatformInput;
import software.wings.graphql.schema.mutation.connector.input.helm.QLAmazonS3PlatformInput.QLAmazonS3PlatformInputBuilder;
import software.wings.graphql.schema.mutation.connector.input.helm.QLGCSPlatformInput;
import software.wings.graphql.schema.mutation.connector.input.helm.QLGCSPlatformInput.QLGCSPlatformInputBuilder;
import software.wings.graphql.schema.mutation.connector.input.helm.QLHelmConnectorInput;
import software.wings.graphql.schema.mutation.connector.input.helm.QLHelmConnectorInput.QLHelmConnectorInputBuilder;
import software.wings.graphql.schema.mutation.connector.input.helm.QLHttpServerPlatformInput;
import software.wings.graphql.schema.mutation.connector.input.helm.QLHttpServerPlatformInput.QLHttpServerPlatformInputBuilder;
import software.wings.graphql.schema.mutation.connector.input.helm.QLOCIPlatformInput;
import software.wings.graphql.schema.mutation.connector.input.helm.QLOCIPlatformInput.QLOCIPlatformInputBuilder;
import software.wings.graphql.schema.mutation.connector.input.nexus.QLNexusConnectorInput;
import software.wings.graphql.schema.mutation.connector.input.nexus.QLNexusConnectorInput.QLNexusConnectorInputBuilder;

import java.util.Collections;

public class Utility {
  public static QLGitConnectorInputBuilder getQlGitConnectorInputBuilder() {
    return QLGitConnectorInput.builder()
        .name(RequestField.ofNullable("NAME"))
        .URL(RequestField.ofNullable("URL"))
        .userName(RequestField.ofNullable("USER"))
        .branch(RequestField.absent())
        .passwordSecretId(RequestField.absent())
        .sshSettingId(RequestField.absent())
        .generateWebhookUrl(RequestField.absent())
        .customCommitDetails(RequestField.absent());
  }

  public static QLUpdateGitConnectorInputBuilder getQlUpdateGitConnectorInputBuilder() {
    return QLUpdateGitConnectorInput.builder()
        .name(RequestField.ofNullable("NAME"))
        .URL(RequestField.ofNullable("URL"))
        .userName(RequestField.ofNullable("USER"))
        .branch(RequestField.absent())
        .passwordSecretId(RequestField.absent())
        .sshSettingId(RequestField.absent())
        .generateWebhookUrl(RequestField.absent())
        .customCommitDetails(RequestField.absent());
  }

  public static QLDockerConnectorInputBuilder getQlDockerConnectorInputBuilder() {
    return QLDockerConnectorInput.builder()
        .name(RequestField.ofNullable("NAME"))
        .URL(RequestField.ofNullable("URL"))
        .userName(RequestField.ofNullable("USER"))
        .delegateSelectors(RequestField.ofNullable(Collections.singletonList("delegateSelector")))
        .passwordSecretId(RequestField.absent());
  }

  public static QLNexusConnectorInputBuilder getQlNexusConnectorInputBuilder() {
    return QLNexusConnectorInput.builder()
        .name(RequestField.ofNullable("NAME"))
        .URL(RequestField.ofNullable("URL"))
        .userName(RequestField.ofNullable("USER"))
        .passwordSecretId(RequestField.absent())
        .version(RequestField.absent());
  }

  public static QLHelmConnectorInputBuilder getQlHelmConnectorInputBuilder(
      QLHttpServerPlatformInput qlHttpServerPlatformInput) {
    return QLHelmConnectorInput.builder()
        .name(RequestField.ofNullable("NAME"))
        .httpServerPlatformDetails(RequestField.ofNullable(qlHttpServerPlatformInput))
        .gcsPlatformDetails(RequestField.absent())
        .amazonS3PlatformDetails(RequestField.absent())
        .ociPlatformDetails(RequestField.absent());
  }

  public static QLHelmConnectorInputBuilder getQlHelmConnectorInputBuilder(QLOCIPlatformInput qlOCIPlatformInput) {
    return QLHelmConnectorInput.builder()
        .name(RequestField.ofNullable("NAME"))
        .httpServerPlatformDetails(RequestField.absent())
        .gcsPlatformDetails(RequestField.absent())
        .amazonS3PlatformDetails(RequestField.absent())
        .ociPlatformDetails(RequestField.ofNullable(qlOCIPlatformInput));
  }

  public static QLHttpServerPlatformInputBuilder getQlHttpServerPlatformInputBuilder() {
    return QLHttpServerPlatformInput.builder()
        .URL(RequestField.ofNullable("URL"))
        .userName(RequestField.ofNullable("USER"))
        .passwordSecretId(RequestField.absent());
  }

  public static QLOCIPlatformInputBuilder getQlOCIPlatformInputBuilder() {
    return QLOCIPlatformInput.builder()
        .URL(RequestField.ofNullable("URL"))
        .userName(RequestField.ofNullable("USER"))
        .passwordSecretId(RequestField.absent());
  }

  public static QLHelmConnectorInputBuilder getQlHelmConnectorInputBuilder(QLGCSPlatformInput qlgcsPlatformInput) {
    return QLHelmConnectorInput.builder()
        .name(RequestField.ofNullable("NAME"))
        .gcsPlatformDetails(RequestField.ofNullable(qlgcsPlatformInput))
        .httpServerPlatformDetails(RequestField.absent())
        .amazonS3PlatformDetails(RequestField.absent())
        .ociPlatformDetails(RequestField.absent());
  }

  public static QLGCSPlatformInputBuilder getQlGCSPlatformInputBuilder() {
    return QLGCSPlatformInput.builder()
        .googleCloudProvider(RequestField.ofNullable("GCP"))
        .bucketName(RequestField.ofNullable("bucketName"));
  }

  public static QLHelmConnectorInputBuilder getQlHelmConnectorInputBuilder(
      QLAmazonS3PlatformInput qlAmazonS3PlatformInput) {
    return QLHelmConnectorInput.builder()
        .name(RequestField.ofNullable("NAME"))
        .amazonS3PlatformDetails(RequestField.ofNullable(qlAmazonS3PlatformInput))
        .httpServerPlatformDetails(RequestField.absent())
        .gcsPlatformDetails(RequestField.absent())
        .ociPlatformDetails(RequestField.absent());
  }

  public static QLAmazonS3PlatformInputBuilder getQlAmazonS3PlatformInputBuilder() {
    return QLAmazonS3PlatformInput.builder()
        .awsCloudProvider(RequestField.ofNullable("AWS"))
        .bucketName(RequestField.ofNullable("bucketName"))
        .region(RequestField.ofNullable("region"));
  }
}
