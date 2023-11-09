/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.secret;

import io.harness.delegate.core.beans.EncryptedDataParams;
import io.harness.delegate.core.beans.EncryptedRecordForDelegateDecryption;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptedRecordData;

import com.google.protobuf.ByteString;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Condition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(uses = {SecretManagerTypePojoProtoMapper.class, EncryptionTypePojoProtoMapper.class},
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedSourcePolicy = ReportingPolicy.ERROR,
    unmappedTargetPolicy = ReportingPolicy.ERROR, collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED)
public interface EncryptedDataRecordPojoProtoMapper {
  EncryptedDataRecordPojoProtoMapper INSTANCE = Mappers.getMapper(EncryptedDataRecordPojoProtoMapper.class);
  @Mapping(target = "uuid", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
  @Mapping(target = "name", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
  @Mapping(target = "path", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
  @Mapping(target = "encryptionKey", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
  @Mapping(target = "backupEncryptionKey",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
  @Mapping(target = "kmsId", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
  @Mapping(target = "backupKmsId", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
  @Mapping(target = "base64Encoded", source = "base64Encoded",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
  @Mapping(target = "params", source = "parameters")
  EncryptedRecordForDelegateDecryption
  map(EncryptedRecordData data);

  @Condition
  default boolean isNotEmptyEncryptedData(char[] encryptedValue) {
    return Objects.nonNull(encryptedValue) && encryptedValue.length > 0;
  }
  default ByteString mapEncryptedValue(char[] encryptedValue) {
    return ByteString.copyFrom(new String(encryptedValue).getBytes(StandardCharsets.UTF_8));
  }

  @Condition
  default boolean isNotEmptyParam(Set<io.harness.security.encryption.EncryptedDataParams> data) {
    return Objects.nonNull(data) && data.size() > 0;
  }
  default List<EncryptedDataParams> mapEncryptedDataParam(
      Set<io.harness.security.encryption.EncryptedDataParams> data) {
    return data.stream()
        .map(params
            -> EncryptedDataParams.newBuilder()
                   .setName(Objects.toString(params.getName(), ""))
                   .setValue(Objects.toString(params.getValue(), ""))
                   .build())
        .collect(Collectors.toList());
  }

  default Map<String, String> mapAdditionalMetadata(AdditionalMetadata metadata) {
    if (Objects.isNull(metadata)) {
      return Map.of();
    }
    var data = metadata.getValues();
    Map<String, String> ret = new HashMap<>();
    data.entrySet().forEach(
        stringObjectEntry -> ret.put(stringObjectEntry.getKey(), stringObjectEntry.getValue().toString()));
    return ret;
  }
}
