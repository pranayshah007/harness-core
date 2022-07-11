/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.spring.converters.refobject;

import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.serializer.spring.ProtoReadConverter;

import com.google.inject.Singleton;
import org.springframework.data.convert.ReadingConverter;

@Singleton
@ReadingConverter
public class RefObjectReadConverter extends ProtoReadConverter<RefObject> {
  public RefObjectReadConverter() {
    super(RefObject.class);
  }
}
