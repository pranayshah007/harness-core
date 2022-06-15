/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)
public final class FileStoreConstants {
  public static final String CREATED_AT = "This is the time at which File was created.";
  public static final String LAST_MODIFIED_AT = "This is the time at which File was last modified.";
  public static final String ROOT_FOLDER_NAME = "Root";
  public static final String ROOT_FOLDER_IDENTIFIER = "Root";
  public static final String ROOT_FOLDER_PARENT_IDENTIFIER = "";
  public static final String ROOT_FOLDER_PATH = "/Root";

  private FileStoreConstants(){};
}
