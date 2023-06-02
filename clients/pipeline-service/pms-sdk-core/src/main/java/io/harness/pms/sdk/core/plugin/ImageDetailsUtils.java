/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plugin;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.grpc.utils.StringValueUtils;
import io.harness.pms.contracts.plan.ImageInformation;

import com.google.protobuf.StringValue;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class ImageDetailsUtils {
  public io.harness.k8s.model.ImageDetails getImageDetails(ImageInformation imageDetails) {
    return io.harness.k8s.model.ImageDetails.builder()
        .name(StringValueUtils.getStringFromStringValue(imageDetails.getImageName()))
        .domainName(StringValueUtils.getStringFromStringValue(imageDetails.getDomainName()))
        .password(StringValueUtils.getStringFromStringValue(imageDetails.getPassword()))
        .registryUrl(StringValueUtils.getStringFromStringValue(imageDetails.getRegistryUrl()))
        .sourceName(StringValueUtils.getStringFromStringValue(imageDetails.getSourceName()))
        .tag(StringValueUtils.getStringFromStringValue(imageDetails.getImageTag()))
        .username(StringValueUtils.getStringFromStringValue(imageDetails.getUsername()))
        .usernameRef(StringValueUtils.getStringFromStringValue(imageDetails.getUsernameRef()))
        .build();
  }

  public ImageInformation getImageDetails(io.harness.k8s.model.ImageDetails imageDetails) {
    ImageInformation.Builder builder = ImageInformation.newBuilder();
    if (isNotBlank(imageDetails.getName())) {
      builder.setImageName(StringValue.of(imageDetails.getName()));
    }
    if (isNotBlank(imageDetails.getDomainName())) {
      builder.setDomainName(StringValue.of(imageDetails.getDomainName()));
    }
    if (isNotBlank(imageDetails.getPassword())) {
      builder.setPassword(StringValue.of(imageDetails.getPassword()));
    }
    if (isNotBlank(imageDetails.getRegistryUrl())) {
      builder.setRegistryUrl(StringValue.of(imageDetails.getRegistryUrl()));
    }
    if (isNotBlank(imageDetails.getSourceName())) {
      builder.setSourceName(StringValue.of(imageDetails.getSourceName()));
    }
    if (isNotBlank(imageDetails.getTag())) {
      builder.setImageTag(StringValue.of(imageDetails.getTag()));
    }
    if (isNotBlank(imageDetails.getUsername())) {
      builder.setUsername(StringValue.of(imageDetails.getUsername()));
    }
    if (isNotBlank(imageDetails.getUsernameRef())) {
      builder.setUsernameRef(StringValue.of(imageDetails.getUsernameRef()));
    }
    return builder.build();
  }
}
