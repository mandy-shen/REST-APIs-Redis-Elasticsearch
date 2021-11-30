package com.mandy.demo.filter;

import com.mandy.demo.util.Constant;
import com.mandy.demo.util.JwtOAuth;
import org.json.JSONObject;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String header = request.getHeader("Authorization");
        String token = JwtOAuth.authorizeToken(header);

        if ("/token".equals(request.getServletPath())) {
            try {
                JwtOAuth.genJwt();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        } else if ("/validate".equals(request.getServletPath())) {
            // path /validate skip filter..
//        } else if (!"Valid Token".equals(token)) {
//            // 401 - UNAUTHORIZED
//            errorResponse(response, token);
//            return;
        }

        filterChain.doFilter(request, response);
    }

    public void errorResponse(HttpServletResponse response, String json) throws IOException {
        JSONObject newJsonObj = new JSONObject();
        newJsonObj.put(Constant.ERROR, json);

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getOutputStream().println(newJsonObj.toString());
    }
}
