/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings;

public final class SettingConstants {
  public static final String LAST_MODIFIED_AT = "Time when the Setting was last modified.";
  public static final String NAME = "Name of the Setting.";
  public static final String IDENTIFIER = "Identifier of the Setting.";
  public static final String IDENTIFIER_KEY = "identifier";
  public static final String CATEGORY = "Category of the Setting.";
  public static final String CATEGORY_KEY = "category";
  public static final String DEFAULT_VALUE = "Default Value of the Setting.";
  public static final String VALUE_TYPE = "Type of Value of the Setting.";
  public static final String ALLOW_OVERRIDES = "Allow override of the Setting in sub-scopes.";
  public static final String ALLOWED_VALUES = "Set of Values allowed for the Setting.";
  public static final String SOURCE = "Source of the setting value";
  public static final String VALUE = "Value of the setting";
  public static final String UPDATE_TYPE =
      "Type of the update operation. When update type is RESTORE, field [value] is ignored";
  public static final String SETTING_UPDATE_REQUEST_LIST = "List of update requests for settings";
  public static final String BATCH_ITEM_RESPONSE_STATUS = "Request status for the corresponding item in batch request";
  public static final String BATCH_ITEM_ERROR_MESSAGE = "Error message";
  public static final String GROUP_ID = "Group Id of the setting";
  public static final String GROUP_KEY = "group";
  public static final String ALLOW_EDIT = "Is the setting editable at the current scope";
  public static final String ALLOWED_SCOPES = "List of scopes where the setting is available";
  public static final String INCLUDES_PARENT_SCOPE =
      "Flag to include the settings which only exist at the parent scopes";
  public static final String INCLUDES_PARENT_SCOPE_KEY = "includeParentScopes";
}
