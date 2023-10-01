/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.core;

import io.harness.beans.CastedField;
import io.harness.beans.RecasterMap;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RecastObjectFactory {
  <T> T createInstance(Class<T> clazz);

  <T> T createInstance(Class<T> clazz, RecasterMap recasterMap);

  Object createGivenInstance(RecasterMap recasterMap);

  Object createInstance(Recaster recaster, CastedField cf, RecasterMap recasterMap);

  List<Object> createList(CastedField mf);

  Map<Object, Object> createMap(CastedField mf);

  Set<Object> createSet(CastedField mf);
}
