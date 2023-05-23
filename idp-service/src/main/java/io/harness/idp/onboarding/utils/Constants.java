/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.IDP)
public class Constants {
  private Constants() {}

  public static final int UI_DEFAULT_PAGE = 0;
  public static final int UI_DEFAULT_PAGE_LIMIT = 10;
  public static final String ORGANIZATION = "Organization";
  public static final String PROJECT = "Project";
  public static final String SERVICE = "Service";
  public static final String BACKSTAGE_HARNESS_ANNOTATION_PROJECT_URL = "harness.io/project-url";
  public static final String BACKSTAGE_HARNESS_ANNOTATION_CD_SERVICE_ID = "harness.io/cd-serviceId";
  public static final String PROJECT_URL = "projectUrl";
  public static final String PIPE_DELIMITER = "|";
  public static final String ENTITY_UNKNOWN_OWNER = "Unknown";
  public static final String ENTITY_UNKNOWN_LIFECYCLE = "Unknown";
  public static final String ENTITY_UNKNOWN_REF = "Unknown";
  public static final int PAGE_LIMIT_FOR_ENTITY_FETCH = 1000;
  public static final String SAMPLE_ENTITY_FOLDER_NAME = "sample";
  public static final String SAMPLE_ENTITY_FILE_NAME = "catalog-info";
  public static final String SAMPLE_ENTITY_CLASSPATH_LOCATION = "configs/catalog-info.yaml";
  public static final String ENTITY_REQUIRED_ERROR_MESSAGE =
      "At-least one entity of type organization / project / service should be provided";
  public static final String SLASH_DELIMITER = "/";
  public static final String YAML_FILE_EXTENSION = ".yaml";
  public static final String SOURCE_FORMAT = "blob";
  public static final String BACKSTAGE_LOCATION_URL_TYPE = "url";
  public static final String STATUS_UPDATE_REASON_FOR_ONBOARDING_COMPLETED =
      "Customer imported harness entities to IDP";
  public static final String SUCCESS_RESPONSE_STRING = "SUCCESS";
}
