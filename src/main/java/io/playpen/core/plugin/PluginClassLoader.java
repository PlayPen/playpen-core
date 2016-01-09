package io.playpen.core.plugin;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class PluginClassLoader extends URLClassLoader {
    private static final Set<PluginClassLoader> allLoaders = new CopyOnWriteArraySet<>();

    static {
        ClassLoader.registerAsParallelCapable();
    }

    public PluginClassLoader(URL[] urls) {
        super(urls);
        allLoaders.add(this);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        return loadClass0(name, resolve, true);
    }

    protected Class<?> loadClass0(String name, boolean resolve, boolean inOthers) throws ClassNotFoundException {
        try {
            return super.loadClass(name, resolve);
        } catch (ClassNotFoundException e) {
        }

        if (inOthers) {
            for (PluginClassLoader loader : allLoaders) {
                try {
                    return loader.loadClass0(name, resolve, false);
                } catch (ClassNotFoundException e) {

                }
            }
        }

        throw new ClassNotFoundException(name);
    }
}
