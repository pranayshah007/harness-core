/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.bugsnag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Created by Praveen
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class BugsnagApplication implements Comparable<BugsnagApplication> {
  private String name;
  private String id;

  @Override
  public int compareTo(BugsnagApplication o) {
    return name.compareTo(o.name);
  }

  @Data
  @Builder
  public static class BugsnagApplications {
    private List<BugsnagApplication> applications;
  }
}
