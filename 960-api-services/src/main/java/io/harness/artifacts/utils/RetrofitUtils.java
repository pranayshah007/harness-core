/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.util.Map;
import lombok.experimental.UtilityClass;
import retrofit2.Response;

@UtilityClass
@OwnedBy(HarnessTeam.CDC)
public class RetrofitUtils {
  public <T> String getErrorBodyDetails(Response<T> response, String key) {
    try {
      final Map<String, Object> errorBody = JsonUtils.asObject(
          response.errorBody() != null ? response.errorBody().string() : "{}", new TypeReference<>() {});
      String errorBodyMessage = errorBody.get(key).toString();
      return EmptyPredicate.isNotEmpty(errorBodyMessage) ? errorBodyMessage : response.message();
    } catch (Exception e) {
      return response.message();
    }
  }
}
