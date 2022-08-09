public class ShowConstantsAction {
    public void setContainer(Container container) {
        super.setContainer(container);
        constants = new HashMap<String, String>();
        for (String key : container.getInstanceNames(String.class)) {
            constants.put(key, container.getInstance(String.class, key));
        }
    }

}