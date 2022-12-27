/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.manifest.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

@OwnedBy(CDP)
public abstract class ManifestHandler<M, S> {
  private final List<M> manifestList;

  @Getter @Setter protected ManifestHandler nextHandler;

  protected ManifestHandler(List<String> manifestContentList, Map<String, Object> overrideProperties) {
    this.manifestList = createManifests(manifestContentList, overrideProperties);
  }

  public abstract Class<M> getManifestContentUnmarshallClass();
  public abstract void applyOverrideProperties(List<M> manifests, Map<String, Object> overrideProperties);
  public abstract S upsert(S chainState, List<M> manifests);
  public abstract S delete(S chainState, List<M> manifests);

  public S upsert(S chainState) {
    return this.upsert(chainState, manifestList);
  }

  public S delete(S chainState) {
    return this.delete(chainState, manifestList);
  }

  protected List<M> createManifests(List<String> manifestContentList, Map<String, Object> overrideProperties) {
    List<M> manifests = manifestContentList.stream().map(this::parseContentToManifest).collect(Collectors.toList());

    if (isNotEmpty(overrideProperties)) {
      applyOverrideProperties(manifests, overrideProperties);
    }

    return manifests;
  }

  protected M parseContentToManifest(String manifestContent) {
    return DefaultManifestContentParser.parseJson(manifestContent, getManifestContentUnmarshallClass());
  }
}
