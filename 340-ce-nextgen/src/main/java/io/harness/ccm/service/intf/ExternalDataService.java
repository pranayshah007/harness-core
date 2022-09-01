package io.harness.ccm.service.intf;

import io.harness.ccm.commons.entities.ExternalDataSource;

import com.google.cloud.bigquery.TableId;

public interface ExternalDataService {
  boolean insertData(TableId tableId, ExternalDataSource externalDataSource);
}
