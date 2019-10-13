package slt.security;

import io.jsonwebtoken.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(1)
@Slf4j
public class SecurityFilter implements Filter {

    private String allowOrigin = null;

    protected String getAllowOrigin() {
        return allowOrigin;
    }

    @Override
    public void init(FilterConfig filterConfig) {

        if (allowOrigin == null) {
            String allowOriginEnv = getFromEnvironment();
            allowOrigin= StringUtils.isEmpty(allowOriginEnv)?"http://localhost:4200":allowOriginEnv;
        }
        log.debug("Security filter init");
        log.debug("Only accepting requests form " + allowOrigin);
    }

    protected String getFromEnvironment() {
        return System.getenv("allow.crossorigin");//NOSONAR
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if ("OPTIONS".equalsIgnoreCase(((HttpServletRequest) request).getMethod())) {
            ((HttpServletResponse) response).setStatus(HttpServletResponse.SC_OK);
            ((HttpServletResponse) response).setHeader("Access-Control-Allow-Origin", getAllowOrigin());
            ((HttpServletResponse) response).setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT");
            ((HttpServletResponse) response).setHeader("Access-Control-Allow-Headers", "Authorization,Access-Control-Allow-Headers,Access-Control-Allow-Origin,Access-Control-Allow-Methods,Content-Type,Authorization");
            chain.doFilter(request, response);
        } else {

            ((HttpServletResponse) response).setHeader("Access-Control-Allow-Origin", getAllowOrigin());
            ((HttpServletResponse) response).setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT");
            ((HttpServletResponse) response).setHeader("Access-Control-Max-Age", "3600");
            ((HttpServletResponse) response).setHeader("Access-Control-Allow-Headers", "Access-Control-Allow-Headers, Access-Control-Allow-Origin,Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");


            HttpServletRequest req = (HttpServletRequest) request;
            log.debug("Starting req : {}", req.getRequestURI());
            String token = req.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer")) {
                String jwtToken = token.substring("Bearer".length() + 1);
                Object userId;
                try {
                    Jws<Claims> claimsJws = Jwts.parser().setSigningKey(SecurityConstants.SECRET.getBytes(StandardCharsets.UTF_8)).parseClaimsJws(jwtToken);
                    userId = claimsJws.getBody().get("userId");
                    UserInfo userInfo = new UserInfo();
                    userInfo.setUserId(Integer.valueOf(userId.toString()));
                    ThreadLocalHolder.getThreadLocal().set(userInfo);
                    chain.doFilter(request, response);
                } catch (ExpiredJwtException expiredEx) {
                    log.debug("ExpiredJWT token.");
                    ((HttpServletResponse) response).sendError(403, "Expired session");
                } catch (MalformedJwtException malformedJwt) {
                    log.debug("Incorrect token");
                    ((HttpServletResponse) response).sendError(403, "Invalid token");
                }
            } else if (isPublicResourceURL((HttpServletRequest) request)) {
                chain.doFilter(request, response);
            } else {
                ((HttpServletResponse) response).sendError(403);
            }
            log.debug("Finish req : {}", req.getRequestURI());
        }
    }

    protected boolean isPublicResourceURL(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/swagger-resources") ||
                request.getRequestURI().startsWith("/webjars/") ||
                request.getRequestURI().startsWith("/api/") ||
                request.getRequestURI().startsWith("/v2/api-docs") ||
                request.getRequestURI().startsWith("/swagger-ui.html") ||
                request.getRequestURI().startsWith("/webhooks/public");
    }

    @Override
    public void destroy() {
      // Do nothing
    }

}