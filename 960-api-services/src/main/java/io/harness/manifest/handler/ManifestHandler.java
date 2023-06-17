/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.manifest.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.manifest.request.ManifestRequest;

import lombok.Getter;
import lombok.Setter;

@OwnedBy(CDP)
public abstract class ManifestHandler<M, S> {
  private ManifestRequest manifestRequest;

  @Getter @Setter protected ManifestHandler nextHandler;

  protected ManifestHandler(ManifestRequest manifestRequest) {
    this.manifestRequest = manifestRequest;
  }

  public abstract Class<M> getManifestContentUnmarshallClass();
  public abstract S upsert(S chainState, ManifestRequest manifestRequest);
  public abstract S delete(S chainState, ManifestRequest manifestRequest);
  public abstract S getManifestTypeContent(S chainState, ManifestRequest manifestRequest);

  public S upsert(S chainState) {
    return this.upsert(chainState, manifestRequest);
  }

  public S delete(S chainState) {
    return this.delete(chainState, manifestRequest);
  }

  protected M parseContentToManifest(String manifestContent) {
    M manifest = DefaultManifestContentParser.parseJson(manifestContent, getManifestContentUnmarshallClass());
    validateManifest(manifest);
    return manifest;
  }

  public S getManifestTypeContent(S chainState) {
    return this.getManifestTypeContent(chainState, manifestRequest);
  }

  protected void validateManifest(M manifest) {}
}
