/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.cvng.models.VerificationType;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;

public abstract class TimeSeriesDataCollectionInfo<T extends ConnectorConfigDTO> extends DataCollectionInfo<T> {
  public VerificationType getVerificationType() {
    return VerificationType.TIME_SERIES;
  }
}
