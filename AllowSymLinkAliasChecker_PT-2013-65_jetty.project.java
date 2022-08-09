public class AllowSymLinkAliasChecker {
    public boolean check(String path, Resource resource)
    {
        try
        {
            File file =resource.getFile();
            if (file==null)
                return false;
            
            // If the file exists
            if (file.exists())
            {
                // we can use the real path method to check the symlinks resolve to the alias
                URI real = file.toPath().toRealPath().toUri();
                if (real.equals(resource.getAlias()))
                {
                    LOG.debug("Allow symlink {} --> {}",resource,real);
                    return true;
                }
            }
            else
            {
                // file does not exists, so we have to walk the path and links ourselves.
                Path p = file.toPath().toAbsolutePath();
                File d = p.getRoot().toFile();
                for (Path e:p)
                {
                    d=new File(d,e.toString());
                    
                    while (d.exists() && Files.isSymbolicLink(d.toPath()))
                    {
                        Path link=Files.readSymbolicLink(d.toPath());
                        if (!link.isAbsolute())
                            link=link.resolve(d.toPath());
                        d=link.toFile().getAbsoluteFile().getCanonicalFile();
                    }
                }
                if (resource.getAlias().equals(d.toURI()))
                {
                    LOG.debug("Allow symlink {} --> {}",resource,d);
                    return true;
                }
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            LOG.ignore(e);
        }
        return false;
    }

}