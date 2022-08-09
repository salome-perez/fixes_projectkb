public class PrototypeFactory {
        public T create() {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
            ByteArrayInputStream bais = null;
            try {
                final ObjectOutputStream out = new ObjectOutputStream(baos);
                out.writeObject(iPrototype);

                bais = new ByteArrayInputStream(baos.toByteArray());
                final ObjectInputStream in = new ObjectInputStream(bais);
                return (T) in.readObject();

            } catch (final ClassNotFoundException ex) {
                throw new FunctorException(ex);
            } catch (final IOException ex) {
                throw new FunctorException(ex);
            } finally {
                try {
                    if (bais != null) {
                        bais.close();
                    }
                } catch (final IOException ex) {
                    // ignore
                }
                try {
                    baos.close();
                } catch (final IOException ex) {
                    // ignore
                }
            }
        }

}