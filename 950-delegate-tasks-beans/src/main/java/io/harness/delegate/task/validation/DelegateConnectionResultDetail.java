/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.validation;

import java.time.OffsetDateTime;
import java.util.Date;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DelegateConnectionResultDetail {
  private String uuid;
  private long lastUpdatedAt;
  private String accountId;
  private String delegateId;
  private String criteria;
  private boolean validated;
  private long duration;
  @Builder.Default private Date validUntil = getValidUntilTime();

  public static Date getValidUntilTime() {
    return Date.from(OffsetDateTime.now().plusDays(30).toInstant());
  }
}
