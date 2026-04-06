package com.eval.gameeval.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.http.server.PathContainer;

import java.io.IOException;
import java.util.Set;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Autowired(required = false)
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        if (!isRouteExists(request)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setHeader("X-Error-Written", "1");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("""
                {
                  "code": 404,
                  "message": "资源不存在",
                  "data": null
                }
            """);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        response.getWriter().write("""
            {
              "code": 401,
              "message": "未登录或登录已过期",
              "data": null
            }
        """);
    }

    private boolean isRouteExists(HttpServletRequest request) {
        if (requestMappingHandlerMapping == null) {
            return true;
        }

        String lookupPath = resolveLookupPath(request);
        String httpMethod = request.getMethod();
        boolean pathMatched = false;
        for (RequestMappingInfo info : requestMappingHandlerMapping.getHandlerMethods().keySet()) {
            if (pathMatches(info, lookupPath)) {
                pathMatched = true;
                if (methodMatches(info, httpMethod)) {
                    return true;
                }
            }
        }
        return pathMatched;
    }

    private String resolveLookupPath(HttpServletRequest request) {
        try {
            if (requestMappingHandlerMapping != null
                    && requestMappingHandlerMapping.getPatternParser() != null) {
                return ServletRequestPathUtils.parseAndCache(request)
                        .pathWithinApplication()
                        .value();
            }
            if (requestMappingHandlerMapping != null) {
                return requestMappingHandlerMapping.getUrlPathHelper()
                        .getPathWithinApplication(request);
            }
            return request.getRequestURI();
        } catch (Exception e) {
            return request.getRequestURI();
        }
    }

    private boolean methodMatches(RequestMappingInfo info, String httpMethod) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        if (methods.isEmpty()) {
            return true;
        }
        try {
            RequestMethod requestMethod = RequestMethod.valueOf(httpMethod);
            return methods.contains(requestMethod);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean pathMatches(RequestMappingInfo info, String lookupPath) {
        PathPatternsRequestCondition pathPatternsCondition = info.getPathPatternsCondition();
        if (pathPatternsCondition != null) {
            PathContainer path = PathContainer.parsePath(lookupPath);
            for (PathPattern pattern : pathPatternsCondition.getPatterns()) {
                if (pattern.matches(path)) {
                    return true;
                }
            }
            return false;
        }

        PatternsRequestCondition legacyCondition = info.getPatternsCondition();
        return legacyCondition != null && !legacyCondition.getMatchingPatterns(lookupPath).isEmpty();
    }
}

