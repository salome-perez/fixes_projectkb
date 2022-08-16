public class Request {
    protected void recycle()
    {
        _authentication=Authentication.NOT_CHECKED;
    	_async.recycle();
        _asyncSupported=true;
        _handled=false;
        if (_context!=null)
            throw new IllegalStateException("Request in context!");
        if(_attributes!=null)
            _attributes.clearAttributes();
        _characterEncoding=null;
        if (_cookies!=null)
            _cookies.reset();
        _cookiesExtracted=false;
        _context=null;
        _serverName=null;
        _method=null;
        _pathInfo=null;
        _port=0;
        _protocol=HttpVersions.HTTP_1_1;
        _queryEncoding=null;
        _queryString=null;
        _requestedSessionId=null;
        _requestedSessionIdFromCookie=false;
        _session=null;
        _requestURI=null;
        _scope=null;
        _scheme=URIUtil.HTTP;
        _servletPath=null;
        _timeStamp=0;
        _timeStampBuffer=null;
        _uri=null;
        if (_baseParameters!=null)
            _baseParameters.clear();
        _parameters=null;
        _paramsExtracted=false;
        _inputState=__NONE;
        
        if (_savedNewSessions!=null)
            _savedNewSessions.clear();
        _savedNewSessions=null;
    }

    public Cookie[] getCookies()
    {
        if (_cookiesExtracted) 
            return _cookies==null?null:_cookies.getCookies();

        _cookiesExtracted = true;
        
        Enumeration enm = _connection.getRequestFields().getValues(HttpHeaders.COOKIE_BUFFER);
        
        // Handle no cookies
        if (enm!=null)
        {
            if (_cookies==null)
                _cookies=new CookieCutter();

            while (enm.hasMoreElements())
            {
                String c = (String)enm.nextElement();
                _cookies.addCookieField(c);
            }
        }

        return _cookies==null?null:_cookies.getCookies();
    }

    public void setCookies(Cookie[] cookies)
    {
        if (_cookies==null)
            _cookies=new CookieCutter();
        _cookies.setCookies(cookies);
    }

}