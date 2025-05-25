package com.hasandag.exchange.common.constants;

public class KafkaConstants {

    public static final String FX_TOPIC_PREFIX = "fx.";
    
    public static final String CONVERSION_COMMAND_TOPIC = FX_TOPIC_PREFIX + "command.conversion";
    public static final String CONVERSION_EVENT_TOPIC = FX_TOPIC_PREFIX + "event.conversion";

    public static final String COMMAND_HANDLER_GROUP = "command-handler-group";
    public static final String EVENT_HANDLER_GROUP = "event-handler-group";
} 