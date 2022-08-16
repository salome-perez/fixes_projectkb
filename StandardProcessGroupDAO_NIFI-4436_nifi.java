public class StandardProcessGroupDAO {
    @Override
    public void verifyActivateControllerServices(final ControllerServiceState state, final Collection<String> serviceIds) {
        final Set<ControllerServiceNode> serviceNodes = serviceIds.stream()
            .map(flowController::getControllerServiceNode)
            .collect(Collectors.toSet());

        for (final ControllerServiceNode serviceNode : serviceNodes) {
            if (state == ControllerServiceState.ENABLED) {
                serviceNode.verifyCanEnable(serviceNodes);
            } else {
                serviceNode.verifyCanDisable(serviceNodes);
            }
        }
    }

    @Override
    public Future<Void> activateControllerServices(final ControllerServiceState state, final Collection<String> serviceIds) {
        final List<ControllerServiceNode> serviceNodes = serviceIds.stream()
            .map(flowController::getControllerServiceNode)
            .collect(Collectors.toList());

        if (state == ControllerServiceState.ENABLED) {
            return flowController.enableControllerServicesAsync(serviceNodes);
        } else {
            return flowController.disableControllerServicesAsync(serviceNodes);
        }
    }

    @Override
    public ProcessGroup disconnectVersionControl(final String groupId) {
        final ProcessGroup group = locateProcessGroup(flowController, groupId);
        group.disconnectVersionControl(true);
        group.onComponentModified();
        return group;
    }

}