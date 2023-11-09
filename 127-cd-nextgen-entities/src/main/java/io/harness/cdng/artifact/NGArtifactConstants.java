/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import lombok.experimental.UtilityClass;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@UtilityClass
public class NGArtifactConstants {
  public static final String ARTIFACT_DIRECTORY = "artifactDirectory";
  public static final String REPOSITORY_NAME = "repositoryName";
  public static final String REPOSITORY = "repository";
  public static final String REPOSITORY_PORT = "repositoryPort";
  public static final String REPOSITORY_FORMAT = "repositoryFormat";
  public static final String ARTIFACT_FILTER = "artifactFilter";
  public static final String REPOSITORY_URL = "repositoryUrl";
  public static final String REGISTRY_HOST_NAME = "registryHostname";
  public static final String REGISTRY = "registry";
  public static final String REGION = "region";
  public static final String TAG = "tag";
  public static final String TAG_INPUT = "tagInput";
  public static final String TAG_REGEX = "tagRegex";
  public static final String VERSION = "version";
  public static final String VERSION_REGEX = "versionRegex";
  public static final String PACKAGE = "package";
  public static final String PACKAGE_NAME = "packageName";
  public static final String PACKAGE_TYPE = "packageType";
  public static final String CONNECTOR_REF = "connectorRef";
  public static final String ACCOUNT = "account";
  public static final String ORG = "org";
  public static final String PROJECT = "project";
  public static final String IMAGE_PATH = "imagePath";
  public static final String REGISTRY_ID = "registryId";
  public static final String ARTIFACT_PATH = "artifactPath";
  public static final String GROUP = "group";
  public static final String GROUP_ID = "groupId";
  public static final String ARTIFACT_ID = "artifactId";
}
