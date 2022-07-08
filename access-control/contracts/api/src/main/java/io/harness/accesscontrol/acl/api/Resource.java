/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl.api;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.IllegalArgumentException;
import io.harness.exception.WingsException;

import java.util.Map;
import javax.annotation.Nullable;
import javax.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.PL)
public class Resource {
  String resourceType;
  String resourceIdentifier;
  Map<String, String> resourceAttributes;

  public static Resource of(@NotEmpty String resourceType, @Nullable String resourceIdentifier) {
    return of(resourceType, resourceIdentifier, null);
  }

  public static Resource of(@NotEmpty String resourceType, @Nullable String resourceIdentifier,
      @Nullable Map<String, String> resourceAttributes) {
    if (isNotEmpty(resourceIdentifier) && isNotEmpty(resourceAttributes)) {
      throw new IllegalArgumentException(
          "Both resource identifier and attributes cannot be provided for access control check", WingsException.USER);
    }
    return Resource.builder()
        .resourceType(resourceType)
        .resourceIdentifier(resourceIdentifier)
        .resourceAttributes(resourceAttributes)
        .build();
  }
}
