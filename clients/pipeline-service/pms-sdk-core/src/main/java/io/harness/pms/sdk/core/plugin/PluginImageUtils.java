/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plugin;

import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.grpc.utils.StringValueUtils;
import io.harness.pms.contracts.plan.ConnectorDetails;
import io.harness.pms.contracts.plan.ImageInformation;
import io.harness.yaml.extended.ci.container.ImageDetails;

import com.google.protobuf.StringValue;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PluginImageUtils {
  ImageDetails getImageDetails(io.harness.pms.contracts.plan.ImageDetails imageDetails) {
    return ImageDetails.builder()
        .imageName(StringValueUtils.getStringFromStringValue(imageDetails.getImageInformation().getImageName()))
        .connectorDetails(ImageDetails.ConnectorDetails.builder()
                              .connectorRef(imageDetails.getConnectorDetails().getConnectorRef())
                              .build())
        .imagePullPolicy(ImagePullPolicy.fromString(
            StringValueUtils.getStringFromStringValue(imageDetails.getImageInformation().getImagePullPolicy())))
        .build();
  }

  io.harness.pms.contracts.plan.ImageDetails getImageDetails(ImageDetails imageDetails) {
    return io.harness.pms.contracts.plan.ImageDetails.newBuilder()
        .setImageInformation(ImageInformation.newBuilder()
                                 .setImageName(StringValue.of(imageDetails.getImageName()))
                                 .setImagePullPolicy(StringValue.of(imageDetails.getImagePullPolicy().getYamlName()))
                                 .build())
        .setConnectorDetails(
            ConnectorDetails.newBuilder().setConnectorRef(imageDetails.getConnectorDetails().getConnectorRef()).build())
        .build();
  }
}
