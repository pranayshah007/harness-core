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
public class CyclonedxDTO extends SbomDTO {
  String bomFormat;
  String specVersion;
  String serialNumber;
  int version;
  Metadata metadata;
  List<Component> components;

  @Override
  public SbomFormat getType() {
    return SbomFormat.CYCLONEDX;
  }

  @Data
  @Builder
  public static class Metadata {
    String timestamp;
    List<CycloneDXVersion> tools;
    Component component;
  }

  @Data
  @Builder
  private static class CycloneDXVersion {
    String vendor;
    String name;
    String version;
  }

  @Data
  @Builder
  public static class Component {
    String bomRef;
    String type;
    String name;
    String version;
    String author;
    String cpe;
    String purl;
    String publisher;
    String description;
    List<License> licenses;
    List<ExternalReference> externalReferences;
    List<Property> properties;
    Swid swid;

    @Data
    @Builder
    public static class License {
      InternalLicence license;
      String expression;

      @Data
      @Builder
      public static class InternalLicence {
        String name;
        String id;
      }
    }

    @Data
    @Builder
    private static class ExternalReference {
      String uRL;
      String type;
    }

    @Data
    @Builder
    private static class Property {
      String name;
      String value;
    }

    @Data
    @Builder
    private static class Swid {
      String tagId;
      String name;
      String version;
    }
  }
}
