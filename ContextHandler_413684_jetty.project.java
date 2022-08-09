public class ContextHandler {
        public boolean check(String path, Resource resource)
        {
            int slash = path.lastIndexOf('/');
            if (slash<0 || resource.exists())
                return false;
            String suffix=path.substring(slash);
            return resource.getAlias().toString().endsWith(suffix);
        }

}