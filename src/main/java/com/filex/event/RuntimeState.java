package com.filex.event;

/**
 * Represents the runtime state of the FileX application.
 *
 * <p>State transitions are published as {@link RuntimeStateChangedEvent}
 * to allow components to react to lifecycle changes.
 */
public enum RuntimeState {
    
    /** Application is initializing core systems. */
    INITIALIZING,
    
    /** Application is starting up (post-initialization). */
    STARTING,
    
    /** Application is fully running and operational. */
    RUNNING,
    
    /** Application is running but one or more core engines failed. */
    DEGRADED,
    
    /** Application is beginning shutdown sequence. */
    STOPPING,
    
    /** Application has fully stopped. */
    STOPPED,
    
    /** Application encountered a fatal error. */
    FAILED
}
