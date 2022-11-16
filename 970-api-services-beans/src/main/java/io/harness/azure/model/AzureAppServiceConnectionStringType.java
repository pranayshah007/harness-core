/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AzureAppServiceConnectionStringType {
  MYSQL("MySQL"),
  SQL_SERVER("SQLServer"),
  SQL_AZURE("SQLAzure"),
  POSTGRE_SQL("PostgreSQL"),
  CUSTOM("Custom");

  private final String value;
  AzureAppServiceConnectionStringType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  public static AzureAppServiceConnectionStringType fromValue(String text) {
    for (AzureAppServiceConnectionStringType connStringType : AzureAppServiceConnectionStringType.values()) {
      if (String.valueOf(connStringType.value).equalsIgnoreCase(text)) {
        return connStringType;
      }
    }
    return null;
  }
}
