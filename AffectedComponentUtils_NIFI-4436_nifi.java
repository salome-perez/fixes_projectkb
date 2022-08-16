public class AffectedComponentUtils {
    public static AffectedComponentEntity updateEntity(final AffectedComponentEntity componentEntity, final NiFiServiceFacade serviceFacade,
                final DtoFactory dtoFactory, final NiFiUser user) {

        switch (componentEntity.getComponent().getReferenceType()) {
            case AffectedComponentDTO.COMPONENT_TYPE_PROCESSOR:
                final ProcessorEntity procEntity = serviceFacade.getProcessor(componentEntity.getId(), user);
                return dtoFactory.createAffectedComponentEntity(procEntity);
            case AffectedComponentDTO.COMPONENT_TYPE_INPUT_PORT: {
                final PortEntity portEntity = serviceFacade.getInputPort(componentEntity.getId(), user);
                return dtoFactory.createAffectedComponentEntity(portEntity, AffectedComponentDTO.COMPONENT_TYPE_INPUT_PORT);
            }
            case AffectedComponentDTO.COMPONENT_TYPE_OUTPUT_PORT: {
                final PortEntity portEntity = serviceFacade.getOutputPort(componentEntity.getId(), user);
                return dtoFactory.createAffectedComponentEntity(portEntity, AffectedComponentDTO.COMPONENT_TYPE_OUTPUT_PORT);
            }
            case AffectedComponentDTO.COMPONENT_TYPE_CONTROLLER_SERVICE:
                final ControllerServiceEntity serviceEntity = serviceFacade.getControllerService(componentEntity.getId(), user);
                return dtoFactory.createAffectedComponentEntity(serviceEntity);
            case AffectedComponentDTO.COMPONENT_TYPE_REMOTE_INPUT_PORT: {
                final RemoteProcessGroupEntity remoteGroupEntity = serviceFacade.getRemoteProcessGroup(componentEntity.getComponent().getProcessGroupId(), user);
                final RemoteProcessGroupContentsDTO remoteGroupContents = remoteGroupEntity.getComponent().getContents();
                final Optional<RemoteProcessGroupPortDTO> portDtoOption = remoteGroupContents.getInputPorts().stream()
                    .filter(port -> port.getId().equals(componentEntity.getId()))
                    .findFirst();

                if (portDtoOption.isPresent()) {
                    final RemoteProcessGroupPortDTO portDto = portDtoOption.get();
                    return dtoFactory.createAffectedComponentEntity(portDto, AffectedComponentDTO.COMPONENT_TYPE_REMOTE_INPUT_PORT, remoteGroupEntity);
                }
                break;
            }
            case AffectedComponentDTO.COMPONENT_TYPE_REMOTE_OUTPUT_PORT: {
                final RemoteProcessGroupEntity remoteGroupEntity = serviceFacade.getRemoteProcessGroup(componentEntity.getComponent().getProcessGroupId(), user);
                final RemoteProcessGroupContentsDTO remoteGroupContents = remoteGroupEntity.getComponent().getContents();
                final Optional<RemoteProcessGroupPortDTO> portDtoOption = remoteGroupContents.getOutputPorts().stream()
                    .filter(port -> port.getId().equals(componentEntity.getId()))
                    .findFirst();

                if (portDtoOption.isPresent()) {
                    final RemoteProcessGroupPortDTO portDto = portDtoOption.get();
                    return dtoFactory.createAffectedComponentEntity(portDto, AffectedComponentDTO.COMPONENT_TYPE_REMOTE_OUTPUT_PORT, remoteGroupEntity);
                }
                break;
            }
        }

        return null;
    }

}