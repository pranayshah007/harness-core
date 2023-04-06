/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plugin;

import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.exception.UnexpectedException;
import io.harness.pms.contracts.plan.ConnectorDetails;
import io.harness.yaml.extended.ci.container.ImageDetails;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PluginImageUtils {
  io.harness.pms.contracts.plan.ImagePullPolicy getImagePullPolicy(ImagePullPolicy imagePullPolicy) {
    switch (imagePullPolicy) {
      case NEVER:
        return io.harness.pms.contracts.plan.ImagePullPolicy.NEVER;
      case ALWAYS:
        return io.harness.pms.contracts.plan.ImagePullPolicy.ALWAYS;
      case IFNOTPRESENT:
        return io.harness.pms.contracts.plan.ImagePullPolicy.IF_NOT_PRESENT;
      default:
        throw new UnexpectedException();
    }
  }

  ImagePullPolicy getImagePullPolicy(io.harness.pms.contracts.plan.ImagePullPolicy imagePullPolicy) {
    if (imagePullPolicy == io.harness.pms.contracts.plan.ImagePullPolicy.NEVER) {
      return ImagePullPolicy.NEVER;
    } else if (io.harness.pms.contracts.plan.ImagePullPolicy.ALWAYS == imagePullPolicy) {
      return ImagePullPolicy.ALWAYS;
    } else if (imagePullPolicy == io.harness.pms.contracts.plan.ImagePullPolicy.IF_NOT_PRESENT) {
      return ImagePullPolicy.IFNOTPRESENT;
    } else {
      throw new UnexpectedException();
    }
  }

  ImageDetails getImageDetails(io.harness.pms.contracts.plan.ImageDetails imageDetails) {
    return ImageDetails.builder()
        .imageName(imageDetails.getImageName())
        .connectorDetails(ImageDetails.ConnectorDetails.builder()
                              .connectorRef(imageDetails.getConnectorDetails().getConnectorRef())
                              .build())
        .imagePullPolicy(getImagePullPolicy(imageDetails.getImagePullPolicy()))
        .build();
  }

  io.harness.pms.contracts.plan.ImageDetails getImageDetails(ImageDetails imageDetails) {
    return io.harness.pms.contracts.plan.ImageDetails.newBuilder()
        .setImageName(imageDetails.getImageName())
        .setConnectorDetails(
            ConnectorDetails.newBuilder().setConnectorRef(imageDetails.getConnectorDetails().getConnectorRef()).build())
        .setImagePullPolicy(getImagePullPolicy(imageDetails.getImagePullPolicy()))
        .build();
  }
}
