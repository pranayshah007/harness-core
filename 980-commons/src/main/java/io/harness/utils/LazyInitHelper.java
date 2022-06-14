/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import io.harness.utils.function.VoidSupplier;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class LazyInitHelper {
  /**
   *
   * @param lock Object - lock object
   * @param load Supplier - function that loads the singleton
   * @param init Action - function that runs the singleton creation block
   * @param <T> singleton type
   * @return T singleton
   */
  public static <T> T apply(Object lock, Supplier<T> load, VoidSupplier init) {
    T ret = load.get();
    // Double check locking pattern
    if (ret == null) { // Check for the first time
      synchronized (lock) { // Check for the second time.
        ret = load.get();
        // if there is no instance available... create new one
        if (ret == null) {
          init.execute();
          ret = load.get();
        }
      }
    }

    return ret;
  }

  /**
   *
   * @param lock Object - lock object
   * @param condition BooleanSupplier - function that returns the condition when singleton is not initialized
   * @param init Action - function that runs the singleton creation block
   */
  public static void apply(Object lock, BooleanSupplier condition, VoidSupplier init) {
    // Double check locking pattern
    if (condition.getAsBoolean()) { // Check for the first time
      synchronized (lock) { // Check for the second time.
        // if there is no instance available... create new one
        if (condition.getAsBoolean()) {
          init.execute();
        }
      }
    }
  }
}
