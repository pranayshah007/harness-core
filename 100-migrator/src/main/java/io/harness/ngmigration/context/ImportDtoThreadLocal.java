/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.context;

import io.harness.ngmigration.dto.ImportDTO;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ImportDtoThreadLocal {
  public static final ThreadLocal<ImportDTO> importDto = new ThreadLocal<>();

  /**
   *
   * @param importDTO
   */
  public static void set(final ImportDTO importDTO) {
    importDto.set(importDTO);
  }

  /**
   * Unset.
   */
  public static void unset() {
    importDto.remove();
  }

  /**
   *
   * @return ImportDTO
   */
  public static ImportDTO get() {
    return importDto.get();
  }
}