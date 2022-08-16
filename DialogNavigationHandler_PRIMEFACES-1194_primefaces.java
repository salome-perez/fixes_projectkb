public class DialogNavigationHandler {
    public void handleNavigation(FacesContext context, String fromAction, String outcome) {
        Map<Object, Object> attrs = context.getAttributes();
        String dialogOutcome = (String) attrs.get(Constants.DIALOG_FRAMEWORK.OUTCOME);

        if (dialogOutcome != null) {
            Map<String, String> requestParams = context.getExternalContext().getRequestParameterMap();
            NavigationCase navCase = getNavigationCase(context, fromAction, dialogOutcome);
            String toViewId = navCase.getToViewId(context);
            Map<String, Object> options = (Map<String, Object>) attrs.get(Constants.DIALOG_FRAMEWORK.OPTIONS);
            Map<String, List<String>> params = (Map<String, List<String>>) attrs.get(Constants.DIALOG_FRAMEWORK.PARAMS);

            if (params == null) {
                params = Collections.emptyMap();
            }

            boolean includeViewParams = false;
            if (options != null && options.containsKey(Constants.DIALOG_FRAMEWORK.INCLUDE_VIEW_PARAMS)) {
                includeViewParams = (Boolean) options.get(Constants.DIALOG_FRAMEWORK.INCLUDE_VIEW_PARAMS);
            }

            String url = context.getApplication().getViewHandler().getBookmarkableURL(context, toViewId, params, includeViewParams);
            url = ComponentUtils.escapeEcmaScriptText(url);

            StringBuilder sb = new StringBuilder();
            String sourceComponentId = (String) attrs.get(Constants.DIALOG_FRAMEWORK.SOURCE_COMPONENT);
            String sourceWidget = (String) attrs.get(Constants.DIALOG_FRAMEWORK.SOURCE_WIDGET);
            String pfdlgcid = requestParams.get(Constants.DIALOG_FRAMEWORK.CONVERSATION_PARAM);
            if (pfdlgcid == null) {
                pfdlgcid = UUID.randomUUID().toString();
            }
            pfdlgcid = ComponentUtils.escapeEcmaScriptText(pfdlgcid);

            sb.append("PrimeFaces.openDialog({url:'").append(url).append("',pfdlgcid:'").append(pfdlgcid)
                    .append("',sourceComponentId:'").append(sourceComponentId).append("'");

            if (sourceWidget != null) {
                sb.append(",sourceWidgetVar:'").append(sourceWidget).append("'");
            }

            sb.append(",options:{");
            if (options != null && options.size() > 0) {
                for (Iterator<String> it = options.keySet().iterator(); it.hasNext();) {
                    String optionName = it.next();
                    Object optionValue = options.get(optionName);

                    sb.append(optionName).append(":");
                    if (optionValue instanceof String) {
                        sb.append("'").append(optionValue).append("'");
                    }
                    else {
                        sb.append(optionValue);
                    }

                    if (it.hasNext()) {
                        sb.append(",");
                    }
                }
            }
            sb.append("}});");

            PrimeFaces.current().executeScript(sb.toString());
            sb.setLength(0);
        }
        else {
            base.handleNavigation(context, fromAction, outcome);
        }
    }

}