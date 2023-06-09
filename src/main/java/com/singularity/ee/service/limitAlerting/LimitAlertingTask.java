package com.singularity.ee.service.limitAlerting;

import com.singularity.ee.agent.appagent.kernel.ServiceComponent;
import com.singularity.ee.agent.appagent.kernel.spi.IDynamicService;
import com.singularity.ee.agent.appagent.kernel.spi.IServiceContext;
import com.singularity.ee.agent.appagent.services.transactionmonitor.common.ResetableCallsLimiter;
import com.singularity.ee.agent.appagent.services.transactionmonitor.common.exitcall.ExitCallRegistry;
import com.singularity.ee.agent.appagent.services.transactionmonitor.nonboot.spi.AgentBusinessTransaction;
import com.singularity.ee.agent.appagent.services.transactionmonitor.nonboot.spi.IBTContext;
import com.singularity.ee.agent.appagent.services.transactionmonitor.serviceendpoints.ServiceEndpointRegistry;
import com.singularity.ee.agent.commonservices.config.ConnectivityEvent;
import com.singularity.ee.agent.commonservices.config.IConnectivityEventListener;
import com.singularity.ee.agent.commonservices.eventgeneration.events.InternalEventGenerator;
import com.singularity.ee.agent.util.ThreadMapContainer;
import com.singularity.ee.agent.util.log4j.ADLoggerFactory;
import com.singularity.ee.agent.util.log4j.IADLogger;
import com.singularity.ee.config.TransactionMonitorProperty;
import com.singularity.ee.controller.api.dto.transactionmonitor.transactiondefinition.BusinessTransaction;
import com.singularity.ee.util.javaspecific.collections.ADConcurrentHashMap;
import com.singularity.ee.util.javaspecific.threads.IAgentRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LimitAlertingTask implements IAgentRunnable {
    private static final IADLogger logger = ADLoggerFactory.getLogger((String)"com.singularity.dynamicservice.limitAlerting.LimitAlertingTask");
    private IDynamicService agentService;
    private AgentNodeProperties agentNodeProperties;
    private ServiceComponent serviceComponent;
    private IServiceContext serviceContext;
    private HashMap<String,Long> messagesSentCacheMap;

    public LimitAlertingTask(IDynamicService agentService, AgentNodeProperties agentNodeProperties, ServiceComponent serviceComponent, IServiceContext iServiceContext) {
        this.agentNodeProperties=agentNodeProperties;
        this.agentService=agentService;
        this.serviceComponent=serviceComponent;
        this.serviceContext=iServiceContext;
        this.messagesSentCacheMap = new HashMap<>();
    }

    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        if(!agentNodeProperties.isEnabled()) {
            logger.info("Service " + agentService.getName() + " is not enabled. To enable it enable the node property "+ AgentNodeProperties.ENABLE_PROPERTY);
            return;
        }
        InternalEventGenerator internalEventGenerator = serviceComponent.getEventHandler().getEventService().getInternalEventGenerator();

        //get any limit that has been hit
        try {
            ADConcurrentHashMap<String, ResetableCallsLimiter> resetableCallsLimiterMap = ReflectionHelper.getCpmLimitersByIdentifierMap(serviceComponent.getReaperBasedCPMLimiterRegistry());
            for( ResetableCallsLimiter resetableCallsLimiter : resetableCallsLimiterMap.getAllValues() ) {
                if( resetableCallsLimiter.toString().contains("limitReached=true") ) sendErrorEvent(resetableCallsLimiter.toString());
            }
        } catch (Exception e) {
            logger.warn(String.format("Can not get Map of Resetable Calls Limits hit, Exception: %s",e),e);
        }

        //look for async segment limits
        for(AgentBusinessTransaction businessTransaction : serviceComponent.getBusinessTransactionRegistry().getAllBusinessTransactions() ) {
            //businessTransaction.
        }

        ServiceEndpointRegistry serviceEndpointRegistry = serviceComponent.getServiceEndpointRegistry();
        try {
            ThreadMapContainer<IBTContext> threadMapContainer = ReflectionHelper.getBTContextMap(serviceComponent.getThreadLocalManager());

            for( Object ibtContextObject : ReflectionHelper.geIBTContexttMapObjects(threadMapContainer).values() ) {
                if (serviceEndpointRegistry.isMaxServiceEndpointPerThreadReached((IBTContext) ibtContextObject)) {
                    sendErrorEvent(String.format("Business Transaction Limit has been reached, no more unique BTs will be registered for BT %s", ((IBTContext) ibtContextObject).getTransientBTName()));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        //BT limit reached
        if( serviceComponent.getBusinessTransactionRegistry().isTransactionLimitReached() ) {
            sendErrorEvent(String.format("Business Transaction Limit has been reached, no more unique BTs will be registered"));
        }

        //look for backend max calls reached
        ExitCallRegistry exitCallRegistry = serviceComponent.getBusinessTransactionRegistry().getExitCallRegistry();
        if( exitCallRegistry.isMaxDiscoveredBackendsLimitReached() ) {
            sendErrorEvent(String.format("Not discovering backend calls beyond %d, because limit of %d set by node property '%s' has been reached",
                    exitCallRegistry.getDiscoveredBackendSize(), agentNodeProperties.getProperty(TransactionMonitorProperty.MAX_DISCOVERED_BACKENDS), TransactionMonitorProperty.MAX_DISCOVERED_BACKENDS));
        }
    }

    private void sendInfoEvent(String message) {
        sendEvent(message, "Info", MetaData.getAsMap());
    }

    private void sendErrorEvent( String message ) { sendEvent(message, "Error", MetaData.getAsMap()); }

    private void sendEvent(String message, String level, Map map) {
        if( alreadySentRecently(message) ) return;
        logger.info("Sending Custom INFO Event with message: "+ message);
        if( !map.containsKey("plugin-version") ) map.putAll(MetaData.getAsMap());
        switch( level.toLowerCase() ) {
            case "error": {
                serviceComponent.getEventHandler().publishErrorEvent(message, map, false);
                break;
            }
            default: {
                serviceComponent.getEventHandler().publishInfoEvent(message, map);
            }
        }
        updateCache(message);
    }

    private void updateCache(String message) {
        this.messagesSentCacheMap.put(message, System.currentTimeMillis());
    }

    private boolean alreadySentRecently(String message) {
        Long timestamp = this.messagesSentCacheMap.get(message);
        if( timestamp == null ) return false;
        if( System.currentTimeMillis() - timestamp > TimeUnit.HOURS.toMillis(1) ) return false;
        return true;
    }


}
