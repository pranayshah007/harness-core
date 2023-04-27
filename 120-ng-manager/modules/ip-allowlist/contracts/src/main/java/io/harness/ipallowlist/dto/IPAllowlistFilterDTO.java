/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ipallowlist.dto;

import io.harness.spec.server.ng.v1.model.AllowedSourceType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IPAllowlistFilterDTO {
  String searchTerm;
  AllowedSourceType allowedSourceType;
}
