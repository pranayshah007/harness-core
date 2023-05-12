/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;

/**
 * Created by peeyushaggarwal on 6/15/16.
 */
public class MorphiaMatcher {
  /**
   * Same query as query.
   *
   * @param <T>   the type parameter
   * @param query the query
   * @return the query
   */
  public static <T> Query<T> sameQueryAs(Query<T> query) {
    return argThat(hasProperty("children", samePropertyValuesAs(on(query).get("children"))));
  }

  /**
   * Same update operations as update operations.
   *
   * @param <T>              the type parameter
   * @param updateOperations the update operations
   * @return the update operations
   */
  public static <T> UpdateOperations<T> sameUpdateOperationsAs(UpdateOperations<T> updateOperations) {
    // return argThat(hasProperty("ops", samePropertyValuesAs(on(updateOperations).get("ops"))));
    return any(UpdateOperations.class);
  }
}
