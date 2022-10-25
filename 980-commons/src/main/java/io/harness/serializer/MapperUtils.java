/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import lombok.experimental.UtilityClass;
import org.modelmapper.Conditions;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;

@UtilityClass
public class MapperUtils {
  public static void mapObject(Object from, Object to) {
    ModelMapper modelMapper = new ModelMapper();
    modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
    modelMapper.map(from, to);
  }

  public static void mapObjectOnlyNonNull(Object from, Object to) {
    ModelMapper modelMapper = new ModelMapper();
    modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
    modelMapper.getConfiguration().setPropertyCondition(Conditions.isNotNull());
    modelMapper.map(from, to);
  }
}
