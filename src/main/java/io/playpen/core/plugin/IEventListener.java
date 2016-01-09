package io.playpen.core.plugin;

public interface IEventListener<T extends IEventListener<T>> {
    void onListenerRegistered(EventManager<T> em);

    void onListenerRemoved(EventManager<T> em);
}
