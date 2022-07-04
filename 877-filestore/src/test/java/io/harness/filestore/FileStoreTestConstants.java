/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class FileStoreTestConstants {
  public static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  public static final String ORG_IDENTIFIER = "orgIdentifier";
  public static final String PROJECT_IDENTIFIER = "projectIdentifier";
  public static final String IDENTIFIER = "identifier";
  public static final String FILE_IDENTIFIER = "fileIdentifier";
  public static final String FILE_NAME = "fileName";
  public static final String FILE_UUID = "fileUuid";
  public static final String PARENT_IDENTIFIER = "parentIdentifier";
  public static final String YML_MIME_TYPE = "yml";
  public static final String ADMIN_USER_NAME = "admin";
  public static final String ADMIN_USER_EMAIL = "admin@harness.io";
  public static final String FOLDER_NAME = "folderName";
  public static final String FOLDER_IDENTIFIER = "identifier";
}
