package io.harness.ccm.commons.entities;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExternalDataSource {
  Integer month;
  Integer year;
  Double cost;
  String datasource;
}