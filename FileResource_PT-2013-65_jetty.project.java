public class FileResource {
    private static URL checkAlias(File file)
    {
        try
        {
            String abs=file.getAbsolutePath();
            String can=file.getCanonicalPath();

            if (!abs.equals(can))
            {
                LOG.debug("ALIAS abs={} can={}",abs,can);
                return new File(can).toURI().toURL();
            }
        }
        catch(IOException e)
        {
            LOG.warn("bad alias for {}: {}",file,e.toString());
            LOG.debug(e);
            try
            {
                return new URL("http://eclipse.org/bad/canonical/alias");
            }
            catch(Exception e2)
            {
                LOG.ignore(e2);
                throw new RuntimeException(e);
            }
        }

        return null;
    }

}