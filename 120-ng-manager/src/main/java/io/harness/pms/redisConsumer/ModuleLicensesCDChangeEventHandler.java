package io.harness.pms.redisConsumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import io.harness.eventHandler.DebeziumAbstractRedisEventHandler;
import lombok.SneakyThrows;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;

public class ModuleLicensesCDChangeEventHandler extends DebeziumAbstractRedisEventHandler {
    @Inject private DSLContext dsl;

    @SneakyThrows
    public Record createRecord(String value, String id) {
        JsonNode node = objectMapper.readTree(value);

        //Record record = dsl.newRecord(Ta)
        return null;
    }

    @Override
    public boolean handleCreateEvent(String id, String value) {
        Record record = createRecord(value, id);
        if (record == null) {
            return true;
        }
        try {
            return true;
        } catch (DataAccessException ex) {
            return false;
        }
    }

    @Override
    public boolean handleDeleteEvent(String id) {
        return false;
    }

    @Override
    public boolean handleUpdateEvent(String id, String value) {
        return false;
    }
}
