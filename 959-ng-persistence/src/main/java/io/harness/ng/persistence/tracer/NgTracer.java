package io.harness.ng.persistence.tracer;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

public interface NgTracer {
  void traceSpringQuery(Query query, Class<?> entityClass, MongoTemplate mongoTemplate);
}
