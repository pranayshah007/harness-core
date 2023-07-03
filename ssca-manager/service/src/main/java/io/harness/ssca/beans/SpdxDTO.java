/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.beans;

import io.harness.ssca.normalize.SbomFormat;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SpdxDTO extends SbomDTO {
  String spdxid;
  String name;
  String spdxVersion;
  CreationInfo creationInfo;
  String dataLicense;
  String documentNamespace;
  List<Package> packages;
  List<Files> files;
  List<Relationships> relationships;

  @Override
  public SbomFormat getType() {
    return SbomFormat.SPDX_JSON;
  }

  @Data
  public class CreationInfo {
    String created;
    List<String> creators;
    String licenseListVersion;
  }

  @Data
  private class Files {
    String spdxid;
    String comment;
    String fileName;
    String licenseConcluded;
  }

  @Data
  public class Package {
    String SPDXID;
    String name;
    String licenseConcluded;
    String description;
    List<ExternalRefs> externalRefs;
    String downloadLocation;
    boolean filesAnalyzed;
    String licenseDeclared;
    String originator;
    String sourceInfo;
    String versionInfo;
    String packageManager;

    @Data
    public class ExternalRefs {
      String referenceCategory;
      String referenceLocator;
      String referenceType;
    }
  }

  @Data
  private class Relationships {
    String relatedSpdxElement;
    String relationshipType;
    String spdxElementID;
  }

  @Data
  private class Attestation {
    String publicKey;
    boolean skipVerification;
  }
}
