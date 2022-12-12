/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.currency;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

@OwnedBy(CE)
@TargetModule(HarnessModule._490_CE_COMMONS)
public class CurrencyConversionFactorTableSchema {
  private enum DataType { STRING, FLOAT, DATE, TIMESTAMP, BOOLEAN }

  public enum Fields {
    ACCOUNT_ID("accountId", DataType.STRING),
    CLOUD_SERVICE_PROVIDER("cloudServiceProvider", DataType.STRING),
    SOURCE_CURRENCY("sourceCurrency", DataType.STRING),
    DESTINATION_CURRENCY("destinationCurrency", DataType.STRING),
    CONVERSION_FACTOR("conversionFactor", DataType.FLOAT),
    MONTH("month", DataType.DATE),
    // Present in the user input table
    CONVERSION_TYPE("conversionType", DataType.STRING),
    // Present in the default table
    CONVERSION_SOURCE("conversionSource", DataType.STRING),
    CREATED_AT("createdAt", DataType.TIMESTAMP),
    UPDATED_AT("updatedAt", DataType.TIMESTAMP),
    // Present in the user input table
    IS_HISTORICAL_UPDATE_REQUIRED("isHistoricalUpdateRequired", DataType.BOOLEAN);

    private final String fieldName;
    private final DataType dataType;

    Fields(final String fieldName, final DataType dataType) {
      this.fieldName = fieldName;
      this.dataType = dataType;
    }

    public String getFieldName() {
      return fieldName;
    }

    public DataType getDataType() {
      return dataType;
    }

    public static Fields fromString(final String text) {
      for (final Fields field : Fields.values()) {
        if (field.getFieldName().equals(text)) {
          return field;
        }
      }
      return null;
    }
  }
}