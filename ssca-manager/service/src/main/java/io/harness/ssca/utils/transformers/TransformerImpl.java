/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.utils.transformers;

import com.google.inject.Inject;
import org.modelmapper.ModelMapper;

public class TransformerImpl implements Transformer {
  @Inject ModelMapper modelMapper;

  @Override
  public <D> D map(Object source, Class<D> destinationType) {
    return modelMapper.map(source, destinationType);
  }
}
