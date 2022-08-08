/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.beans.cvnglog.CVNGLogType;
import io.harness.cvng.beans.cvnglog.TraceableType;
import io.harness.cvng.core.entities.cvnglogs.ApiCallLogRecord;
import io.harness.cvng.core.entities.cvnglogs.CVNGLogRecord;
import io.harness.cvng.core.entities.cvnglogs.ExecutionLogRecord;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@FieldNameConstants(innerTypeName = "CVNGLogKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity(value = "cvngLogs", noClassnameStored = true)
@HarnessEntity(exportable = true)
@SuperBuilder
@OwnedBy(HarnessTeam.CV)
@StoreIn(DbAliases.CVNG)
public final class CVNGLog implements PersistentEntity, UuidAware, AccountAccess, UpdatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("query_idx")
                 .field(CVNGLogKeys.accountId)
                 .field(CVNGLogKeys.logType)
                 .field(CVNGLogKeys.traceableType)
                 .field(CVNGLogKeys.traceableId)
                 .field(CVNGLogKeys.endTime)
                 .field(CVNGLogKeys.startTime)
                 .build())
        .build();
  }
  List<CVNGLogRecord> logRecords;

  @Id private String uuid;
  private String accountId;
  private String traceableId;
  private Instant startTime;
  private Instant endTime;
  private TraceableType traceableType;
  private CVNGLogType logType;
  private long lastUpdatedAt;
  @JsonIgnore
  @SchemaIgnore
  @Builder.Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(1).toInstant());

  public static CVNGLogRecord toCVNGLogRecord(CVNGLogDTO cvngLogDTO) {
    switch (cvngLogDTO.getType()) {
      case API_CALL_LOG:
        return ApiCallLogRecord.toCVNGLogRecord(cvngLogDTO);
      case EXECUTION_LOG:
        return ExecutionLogRecord.toCVNGLogRecord(cvngLogDTO);
      default:
        throw new IllegalStateException("CVNG Logs: Log Type cannot be null");
    }
  }

  public List<CVNGLogDTO> toCVNGLogDTOs() {
    List<CVNGLogDTO> cvngLogDTOS = new ArrayList<>();
    logRecords.forEach(logRecord -> {
      CVNGLogDTO cvngLogDTO = logRecord.toCVNGLogDTO();
      cvngLogDTO.setTags(logRecord.getTags());
      commonFieldsSetUp(cvngLogDTO);
      cvngLogDTOS.add(cvngLogDTO);
    });
    return cvngLogDTOS;
  }

  private void commonFieldsSetUp(CVNGLogDTO cvngLogDTO) {
    cvngLogDTO.setAccountId(accountId);
    cvngLogDTO.setTraceableId(traceableId);
    cvngLogDTO.setTraceableType(traceableType);
    cvngLogDTO.setStartTime(startTime.toEpochMilli());
    cvngLogDTO.setEndTime(endTime.toEpochMilli());
  }
}
