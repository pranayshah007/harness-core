/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.encoding.EncodingUtils.compressString;
import static io.harness.data.encoding.EncodingUtils.deCompressString;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.GoogleDataStoreAware.readBlob;
import static io.harness.persistence.GoogleDataStoreAware.readLong;
import static io.harness.persistence.GoogleDataStoreAware.readString;

import static java.lang.System.currentTimeMillis;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.exception.WingsException;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.GoogleDataStoreAware;
import io.harness.persistence.UuidAware;
import io.harness.serializer.JsonUtils;

import software.wings.beans.dto.ThirdPartyApiCallLog.ThirdPartyApiCallField;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.cloud.datastore.Blob;
import com.google.cloud.datastore.BlobValue;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.LongValue;
import com.google.cloud.datastore.StringValue;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import org.apache.http.HttpStatus;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by rsingh on 1/8/18.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"validUntil"})
@EqualsAndHashCode(callSuper = false, exclude = {"validUntil"})
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "ThirdPartyApiCallLogKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "thirdPartyApiCallLog", noClassnameStored = true)
@HarnessEntity(exportable = false)
@TargetModule(HarnessModule._960_API_SERVICES)
public class ThirdPartyApiCallLog implements GoogleDataStoreAware, CreatedAtAware, UuidAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("queryIdx")
                 .field(ThirdPartyApiCallLogKeys.stateExecutionId)
                 .descSortField(ThirdPartyApiCallLogKeys.createdAt)
                 .build())
        .build();
  }
  public static final String NO_STATE_EXECUTION_ID = "NO_STATE_EXECUTION";
  private static final int MAX_JSON_RESPONSE_LENGTH = 16384;
  public static final String PAYLOAD = "Payload";
  public static final String RESPONSE_BODY = "Response Body";
  public static final String STATUS_CODE = "Status Code";

  @NotEmpty private String stateExecutionId;
  @FdIndex @NotEmpty private String accountId;
  @NotEmpty private String delegateId;
  @NotEmpty private String delegateTaskId;
  private String title;
  private List<ThirdPartyApiCallField> request;
  private List<ThirdPartyApiCallField> response;
  private long requestTimeStamp;
  private long responseTimeStamp;
  private long createdAt;
  @Id private String uuid;

  @Default
  @JsonIgnore
  @SchemaIgnore
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusWeeks(2).toInstant());

  @Override
  public com.google.cloud.datastore.Entity convertToCloudStorageEntity(Datastore datastore) {
    Key taskKey = datastore.newKeyFactory()
                      .setKind(this.getClass().getAnnotation(dev.morphia.annotations.Entity.class).value())
                      .newKey(generateUuid());
    try {
      com.google.cloud.datastore.Entity.Builder logEntityBuilder =
          com.google.cloud.datastore.Entity.newBuilder(taskKey)
              .set(ThirdPartyApiCallLogKeys.stateExecutionId, getStateExecutionId())
              .set(ThirdPartyApiCallLogKeys.title,
                  StringValue.newBuilder(getTitle()).setExcludeFromIndexes(true).build())
              .set(ThirdPartyApiCallLogKeys.requestTimeStamp,
                  LongValue.newBuilder(getRequestTimeStamp()).setExcludeFromIndexes(true).build())
              .set(ThirdPartyApiCallLogKeys.responseTimeStamp,
                  LongValue.newBuilder(getResponseTimeStamp()).setExcludeFromIndexes(true).build())
              .set(ThirdPartyApiCallLogKeys.request,
                  BlobValue.newBuilder(Blob.copyFrom(compressString(JsonUtils.asJson(getRequest()))))
                      .setExcludeFromIndexes(true)
                      .build())
              .set(ThirdPartyApiCallLogKeys.response,
                  BlobValue.newBuilder(Blob.copyFrom(compressString(JsonUtils.asJson(getResponse()))))
                      .setExcludeFromIndexes(true)
                      .build())
              .set(CREATED_AT_KEY, currentTimeMillis());
      if (isNotEmpty(getAccountId())) {
        logEntityBuilder.set(ThirdPartyApiCallLogKeys.accountId,
            StringValue.newBuilder(getAccountId()).setExcludeFromIndexes(true).build());
      }
      if (isNotEmpty(getDelegateId())) {
        logEntityBuilder.set(ThirdPartyApiCallLogKeys.delegateId,
            StringValue.newBuilder(getDelegateId()).setExcludeFromIndexes(true).build());
      }
      if (isNotEmpty(getDelegateTaskId())) {
        logEntityBuilder.set(ThirdPartyApiCallLogKeys.delegateTaskId,
            StringValue.newBuilder(getDelegateTaskId()).setExcludeFromIndexes(true).build());
      }
      if (validUntil != null) {
        logEntityBuilder.set(ThirdPartyApiCallLogKeys.validUntil, validUntil.getTime());
      }
      return logEntityBuilder.build();
    } catch (IOException e) {
      throw new WingsException(e);
    }
  }
  @Override
  public GoogleDataStoreAware readFromCloudStorageEntity(com.google.cloud.datastore.Entity entity) {
    final ThirdPartyApiCallLog apiCallLog =
        ThirdPartyApiCallLog.builder()
            .uuid(entity.getKey().getName())
            .stateExecutionId(readString(entity, ThirdPartyApiCallLogKeys.stateExecutionId))
            .accountId(readString(entity, ThirdPartyApiCallLogKeys.accountId))
            .delegateId(readString(entity, ThirdPartyApiCallLogKeys.delegateId))
            .delegateTaskId(readString(entity, ThirdPartyApiCallLogKeys.delegateTaskId))
            .title(readString(entity, ThirdPartyApiCallLogKeys.title))
            .requestTimeStamp(readLong(entity, ThirdPartyApiCallLogKeys.requestTimeStamp))
            .responseTimeStamp(readLong(entity, ThirdPartyApiCallLogKeys.responseTimeStamp))
            .createdAt(readLong(entity, ThirdPartyApiCallLogKeys.createdAt))
            .build();
    try {
      byte[] requestBlob = readBlob(entity, ThirdPartyApiCallLogKeys.request);
      if (isNotEmpty(requestBlob)) {
        apiCallLog.setRequest(
            JsonUtils.asObject(deCompressString(requestBlob), new TypeReference<List<ThirdPartyApiCallField>>() {}));
      }
      byte[] responseBlob = readBlob(entity, ThirdPartyApiCallLogKeys.response);
      if (isNotEmpty(responseBlob)) {
        apiCallLog.setResponse(
            JsonUtils.asObject(deCompressString(responseBlob), new TypeReference<List<ThirdPartyApiCallField>>() {}));
      }
    } catch (IOException e) {
      throw new WingsException(e);
    }
    return apiCallLog;
  }

  public ExecutionStatus getStatus() {
    /*
     In an unexpected scenario where the response is an empty list of fields,
     we report it as a failed API Call
     This can only happen if the addFieldToResponse(...) method was never invoked
     on this instance.
     This is also for preventing NPE while getting 0th index of response
     */
    if (this.response != null && this.response.isEmpty()) {
      return ExecutionStatus.FAILED;
    }
    if (this.response != null
        && this.response.stream().anyMatch(
            obj -> obj.getName().equals(STATUS_CODE) && !obj.getValue().equals(String.valueOf(HttpStatus.SC_OK)))) {
      return ExecutionStatus.FAILED;
    }

    return ExecutionStatus.SUCCESS;
  }

  public static ThirdPartyApiCallLog fromDto(software.wings.beans.dto.ThirdPartyApiCallLog dto) {
    return ThirdPartyApiCallLog.builder()
        .stateExecutionId(dto.getStateExecutionId())
        .accountId(dto.getAccountId())
        .delegateId(dto.getDelegateId())
        .delegateTaskId(dto.getDelegateTaskId())
        .title(dto.getTitle())
        .request(dto.getRequest())
        .response(dto.getResponse())
        .requestTimeStamp(dto.getRequestTimeStamp())
        .responseTimeStamp(dto.getResponseTimeStamp())
        .createdAt(dto.getCreatedAt())
        .uuid(dto.getUuid())
        .build();
  }
}
