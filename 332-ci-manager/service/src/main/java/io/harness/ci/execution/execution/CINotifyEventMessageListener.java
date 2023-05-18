package io.harness.ci.execution;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.harness.eventsframework.consumer.Message;
import io.harness.pms.events.base.PmsAbstractMessageListener;
import io.harness.pms.sdk.execution.events.NotifyEventHandler;
import io.harness.waiter.notify.NotifyEventProto;

import java.util.concurrent.ExecutorService;

import static io.harness.pms.sdk.PmsSdkModuleUtils.CORE_EXECUTOR_NAME;
import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_SERVICE_NAME;

public class CINotifyEventMessageListener extends PmsAbstractMessageListener<NotifyEventProto, NotifyEventHandler> {
    @Inject
    public CINotifyEventMessageListener(@Named(SDK_SERVICE_NAME) String serviceName,
                                         NotifyEventHandler notifyEventHandler, @Named(CORE_EXECUTOR_NAME) ExecutorService executorService) {
        super(serviceName, NotifyEventProto.class, notifyEventHandler, executorService);
    }

    @Override
    public boolean isProcessable(Message message) {
        return true;
    }
}