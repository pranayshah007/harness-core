/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc.scheduler.mapper;

import io.harness.delegate.core.beans.EmptyDirVolume;
import io.harness.delegate.core.beans.HostPathVolume;
import io.harness.delegate.core.beans.PVCVolume;
import io.harness.delegate.core.beans.Resource;
import io.harness.delegate.core.beans.ResourceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.mapstruct.IgnoreProtobufFields;

import com.google.protobuf.Any;
import com.google.protobuf.GeneratedMessageV3;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ValueMapping;
import org.mapstruct.factory.Mappers;

@Mapper(collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface ResourceMapper {
  ResourceMapper INSTANCE = Mappers.getMapper(ResourceMapper.class);
  default Resource map(io.harness.delegate.Resource resource) {
    final var type = mapResourceType(resource.getType());
    final GeneratedMessageV3 res;
    if (resource.hasEmptyDir()) {
      res = mapEmptyDir(resource.getEmptyDir());
    } else if (resource.hasPvc()) {
      res = mapPvc(resource.getPvc());
    } else if (resource.hasHostPath()) {
      res = mapHostPath(resource.getHostPath());
    } else {
      throw new InvalidArgumentsException("Unsupported resource type " + resource.getType());
    }
    return Resource.newBuilder().setSpec(Any.pack(res)).setType(type).build();
  }

  @ValueMapping(source = "RES_UNKNOWN", target = MappingConstants.THROW_EXCEPTION)
  ResourceType mapResourceType(io.harness.delegate.ResourceType resource);
  @IgnoreProtobufFields EmptyDirVolume mapEmptyDir(io.harness.delegate.EmptyDirVolume resource);
  @IgnoreProtobufFields PVCVolume mapPvc(io.harness.delegate.PVCVolume resource);
  @IgnoreProtobufFields HostPathVolume mapHostPath(io.harness.delegate.HostPathVolume resource);
}
