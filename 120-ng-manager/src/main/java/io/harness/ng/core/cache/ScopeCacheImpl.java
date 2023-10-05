/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.cache;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeCacheValue;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.function.Function;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class ScopeCacheImpl implements ScopeCache {
  @Inject @Named("SCOPE_CACHE") private Cache<String, ScopeCacheValue> scopeCache;

  // @Inject @Named("SCOPE_CACHE") private Cache<Scope, ScopeCacheValue> scopeCache; ??

  public static final String PATH_DELIMITER = "/";
  public static final String ACCOUNT = "ACCOUNT";
  public static final String ORGANIZATION = "ORGANIZATION";
  public static final String PROJECT = "PROJECT";

  @Override
  public ScopeCacheValue get(Scope key, Function<String, ScopeCacheValue> mappingFunction) {
    String flatKey = getFlatKey(key);
    try {
      return scopeCache.get(flatKey);
    } catch (Exception e) {
      log.error("Cache get operation failed unexpectedly", e);
      return mappingFunction.apply(flatKey);
    }
  }

  @Override
  public void put(Scope key, @NotEmpty ScopeCacheValue value) {
    String flatKey = getFlatKey(key);
    try {
      scopeCache.put(flatKey, value);
    } catch (Exception e) {
      log.error("Cache put operation failed unexpectedly", e);
    }
  }

  @Override
  public void remove(Scope key) {
    String flatKey = getFlatKey(key);
    try {
      scopeCache.remove(flatKey);
    } catch (Exception e) {
      log.error("Cache put operation failed unexpectedly", e);
    }
  }

  private String getFlatKey(Scope key) {
    if (isEmpty(key.getAccountIdentifier())) {
      throw new InvalidRequestException("Invalid Scope info: accountIdentifier cannot be empty");
    }
    String flat = PATH_DELIMITER + ACCOUNT + key.getAccountIdentifier();
    if (isNotEmpty(key.getOrgIdentifier())) {
      flat.concat(PATH_DELIMITER + ORGANIZATION + key.getOrgIdentifier());
      if (isNotEmpty(key.getProjectIdentifier())) {
        flat.concat(PATH_DELIMITER + PROJECT + key.getProjectIdentifier());
      }
    }
    return flat;
  }
}
