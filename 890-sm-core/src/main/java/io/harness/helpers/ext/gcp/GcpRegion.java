/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helpers.ext.gcp;

public enum GcpRegion {
  US_EAST_1("us-east1"),
  US_EAST_2("us-east2"),
  US_EAST_4("us-east4"),
  US_WEST_1("us-west1"),
  US_WEST_2("us-west2"),
  US_WEST_3("us-west3"),
  US_WEST_4("us-west4"),
  US_CENTRAL_1("us-central1"),
  US_CENTRAL_2("us-central2"),
  US_CENTRAL_3("us-central3"),
  US_CENTRAL_4("us-central4"),

  EU_WEST_1("eu-west1"),
  EU_WEST_2("eu-west2"),
  EU_WEST_3("eu-west3"),
  EU_WEST_4("eu-west4"),
  EU_WEST_6("eu-west6"),
  EU_NORTH_1("eu-north1"),
  EU_CENTRAL_1("eu-central1"),
  EU_CENTRAL_2("eu-central2"),
  EU_CENTRAL_3("eu-central3"),
  EU_CENTRAL_4("eu-central4"),
  EU_SOUTH_1("eu-south1"),
  EU_SOUTH_2("eu-south2"),
  EU_SOUTH_3("eu-south3"),
  EU_SOUTH_4("eu-south4"),

  ASIA_EAST_1("asia-east1"),
  ASIA_EAST_2("asia-east2"),

  ASIA_NORTHEAST_1("asia-northeast1"),

  ASIA_SOUTHEAST_1("asia-southeast1"),
  ASIA_SOUTHEAST_2("asia-southeast2"),

  ASIA_SOUTH_1("asia-south1"),
  ASIA_SOUTH_2("asia-south2"),
  ASIA_SOUTH_3("asia-south3"),
  ASIA_SOUTH_4("asia-south4"),

  NA_NORTHEAST_1("northamerica-northeast1"),
  SA_EAST_1("southamerica-east1");

  public static final GcpRegion DEFAULT_REGION = US_WEST_2;
  private final String name;

  GcpRegion(String name) {
    this.name = name;
  }

  public String getName() {
    return this.name;
  }

  public static GcpRegion fromName(String regionName) {
    GcpRegion[] values = values();
    for (GcpRegion region : values) {
      if (region.getName().equals(regionName)) {
        return region;
      }
    }
    throw new IllegalArgumentException("Cannot create enum from " + regionName + " value!");
  }
}
