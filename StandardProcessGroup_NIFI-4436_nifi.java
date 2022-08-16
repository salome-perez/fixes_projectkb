public class StandardProcessGroup {
    @Override
    public Set<ControllerServiceNode> getControllerServices(final boolean recursive) {
        final Set<ControllerServiceNode> services = new HashSet<>();

        readLock.lock();
        try {
            services.addAll(controllerServices.values());
        } finally {
            readLock.unlock();
        }

        if (recursive) {
            final ProcessGroup parentGroup = parent.get();
            if (parentGroup != null) {
                services.addAll(parentGroup.getControllerServices(true));
            }
        }

        return services;
    }

}