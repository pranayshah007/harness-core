/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc.scheduler.mapper;

import io.harness.delegate.core.beans.SecurityContext;

public interface SecurityContextMapper {
  // TODO: Convert to MapStruct, once proto-spi extension library gets AppSec approved or we implement our own SPI
  // extension, because base MapStruct does not support protobuf types naming conventions very well out of the box
  // (lists in particular)
  static SecurityContext map(io.harness.delegate.SecurityContext context) {
    if (context == null) {
      return null;
    }

    final var securityContext = SecurityContext.newBuilder();

    securityContext.setAllowPrivilegeEscalation(context.getAllowPrivilegeEscalation());
    securityContext.setPrivileged(context.getPrivileged());
    securityContext.setProcMount(context.getProcMount());
    securityContext.setProcMountBytes(context.getProcMountBytes());
    securityContext.setReadOnlyRootFilesystem(context.getReadOnlyRootFilesystem());
    securityContext.setRunAsNonRoot(context.getRunAsNonRoot());
    securityContext.setRunAsGroup(context.getRunAsGroup());
    securityContext.setRunAsUser(context.getRunAsUser());

    for (final String addCapability : context.getAddCapabilityList()) {
      securityContext.addAddCapability(addCapability);
    }

    for (final String dropCapability : context.getDropCapabilityList()) {
      securityContext.addDropCapability(dropCapability);
    }

    return securityContext.build();
  }
}
