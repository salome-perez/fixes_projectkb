public class InvokerTransformer {
    private void readObject(ObjectInputStream is) throws ClassNotFoundException, IOException {
        FunctorUtils.checkUnsafeSerialization(InvokerTransformer.class);
        is.defaultReadObject();
    }

    private void writeObject(ObjectOutputStream os) throws IOException {
        FunctorUtils.checkUnsafeSerialization(InvokerTransformer.class);
        os.defaultWriteObject();
    }

}