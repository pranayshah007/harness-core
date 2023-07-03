/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.normalize;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@OwnedBy(HarnessTeam.SSCA)
@Singleton
public class NormalizerRegistry {
  @Inject private Injector injector;

  private final Map<String, Class<? extends Normalizer>> registeredNormalizers = new HashMap<>();

  public NormalizerRegistry() {
    registeredNormalizers.put(SbomFormat.SPDX_JSON.getName(), SpdxNormalizer.class);
    registeredNormalizers.put(SbomFormat.CYCLONEDX.getName(), CyclonedxNormalizer.class);
  }

  public Optional<Normalizer> getNormalizer(String format) {
    if (!registeredNormalizers.containsKey(format)) {
      return Optional.empty();
    }
    return Optional.of(injector.getInstance(registeredNormalizers.get(format)));
  }
}
