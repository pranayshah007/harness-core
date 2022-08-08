/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import java.util.List;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@UtilityClass
public class PageTestUtils {
  public static Page getPage(List list, int total) {
    return new PageImpl(list, new Pageable() {
      @Override
      public int getPageNumber() {
        return 0;
      }

      @Override
      public int getPageSize() {
        return 0;
      }

      @Override
      public long getOffset() {
        return 0;
      }

      @Override
      public Sort getSort() {
        return null;
      }

      @Override
      public Pageable next() {
        return null;
      }

      @Override
      public Pageable previousOrFirst() {
        return null;
      }

      @Override
      public Pageable first() {
        return null;
      }

      @Override
      public boolean hasPrevious() {
        return false;
      }

      @Override
      public Pageable withPage(int pageNumber) {
        return null;
      }

    }, total);
  }
}
