/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc.scheduler.mapper;

import io.harness.delegate.ComputingResource;
import io.harness.delegate.core.beans.ResourceRequirements;
import io.harness.mapstruct.IgnoreProtobufFields;

import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.factory.Mappers;

@Mapper(collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface ComputeMapper {
  ComputeMapper INSTANCE = Mappers.getMapper(ComputeMapper.class);

  @IgnoreProtobufFields
  @Mapping(target = "mergeTimeout", ignore = true)
  @Mapping(target = "timeout", ignore = true)
  @Mapping(target = "replicas", ignore = true)
  ResourceRequirements map(ComputingResource compute);
}
