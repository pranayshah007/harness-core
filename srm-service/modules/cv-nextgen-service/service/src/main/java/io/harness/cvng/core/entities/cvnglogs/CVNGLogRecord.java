/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities.cvnglogs;

import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.beans.cvnglog.CVNGLogTag;
import io.harness.metrics.service.api.MetricService;
import io.harness.persistence.CreatedAtAware;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.Singular;

@Getter
@Setter
public abstract class CVNGLogRecord implements CreatedAtAware {
  @Singular private List<CVNGLogTag> tags;
  private long createdAt;
  public abstract CVNGLogDTO toCVNGLogDTO();
  public abstract boolean isErrorLog();

  public static class CVNGLogRecordComparator implements Comparator<CVNGLogRecord> {
    @Override
    public int compare(CVNGLogRecord o1, CVNGLogRecord o2) {
      Long c1 = o1.getCreatedAt();
      Long c2 = o2.getCreatedAt();
      return c2.compareTo(c1);
    }
  }

  public abstract void recordsMetrics(MetricService metricService, Map<String, String> tags);
}
