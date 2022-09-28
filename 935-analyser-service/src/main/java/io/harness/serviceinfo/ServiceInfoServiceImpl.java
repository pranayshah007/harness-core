/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serviceinfo;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class ServiceInfoServiceImpl implements ServiceInfoService {
  @Inject private MongoTemplate mongoTemplate;

  public final LoadingCache<String, Optional<ServiceInfo>> serviceInfoCache =
      CacheBuilder.newBuilder()
          .maximumSize(20)
          .expireAfterWrite(1, TimeUnit.HOURS)
          .build(new CacheLoader<String, Optional<ServiceInfo>>() {
            @Override
            public Optional<ServiceInfo> load(String serviceId) {
              return Optional.ofNullable(
                  mongoTemplate.findOne(query(where("serviceId").is(serviceId)), ServiceInfo.class));
            }
          });

  @SneakyThrows
  @Override
  public boolean updateLatest(String serviceId, String version) {
    Optional<ServiceInfo> serviceInfoOptional = serviceInfoCache.get(serviceId);
    if (!serviceInfoOptional.isPresent()) {
      ServiceInfo insertedInfo = mongoTemplate.insert(
          ServiceInfo.builder().uuid(generateUuid()).serviceId(serviceId).version(version).build());
      serviceInfoCache.put(serviceId, Optional.of(insertedInfo));
      return true;
    }
    ServiceInfo serviceInfo = serviceInfoOptional.get();
    if (serviceInfo.getVersions().contains(version)) {
      // All information already updated
      return true;
    }
    serviceInfoCache.invalidate(serviceId);
    serviceInfoOptional = serviceInfoCache.get(serviceId);
    if (serviceInfoOptional.isPresent()) {
      Query query = query(where("serviceId").is(serviceId));
      Update update = new Update().addToSet("versions", version);
      ServiceInfo andModify = mongoTemplate.findAndModify(query, update, ServiceInfo.class);
      serviceInfoCache.put(serviceId, Optional.ofNullable(andModify));
      return andModify != null;
    } else {
      throw new GeneralException("Failed to update service info");
    }
  }

  @Override
  public List<String> getAllVersions(String service) {
    ServiceInfo serviceInfo = mongoTemplate.findOne(query(where("serviceId").is(service)), ServiceInfo.class);
    if (serviceInfo == null) {
      return Collections.emptyList();
    }
    return serviceInfo.getVersions().stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
  }

  @Override
  public List<ServiceInfo> getAllServices() {
    return mongoTemplate.findAll(ServiceInfo.class);
  }
}
