package net.thechunk.playpen.plugin;

import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Log4j2
public class EventManager<T extends IEventListener<T>> {
    private List<T> listeners = new CopyOnWriteArrayList<>();

    public boolean registerListener(T listener) {
        if(listeners.contains(listener))
            return false;

        listeners.add(listener);
        listener.onListenerRegistered(this);
        return true;
    }

    public boolean removeListener(T listener) {
        if(listeners.remove(listener)) {
            listener.onListenerRemoved(this);
            return true;
        }

        return false;
    }

    /**
     * @param call
     * @return True if no errors occured, false if there were errors.
     */
    public boolean callEvent(Consumer<T> call) {
        boolean result = true;
        for(T listener : listeners) {
            try {
                call.accept(listener);
            }
            catch(Exception e) {
                log.error("Unable to pass event to listener " + (listener == null ? "null" : listener.getClass()), e);
                result = false;
            }
        }

        return result;
    }
}
