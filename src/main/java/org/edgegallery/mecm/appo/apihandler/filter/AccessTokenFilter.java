/*
 *    Copyright 2020 Huawei Technologies Co., Ltd.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.edgegallery.mecm.appo.apihandler.filter;

import java.io.IOException;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.ResourceServerTokenServicesConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Import({ResourceServerTokenServicesConfiguration.class})
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class AccessTokenFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessTokenFilter.class);
    private static final String INVALID_TOKEN_MESSAGE = "Invalid access token";
    public static final String HEALTH_URI = "/appo/v1/health";

    @Autowired
    TokenStore jwtTokenStore;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // Skip token check for health check URI
        if (request.getRequestURI() == null || !request.getRequestURI().equals(HEALTH_URI)) {
            String accessTokenStr = request.getHeader("access_token");
            if (StringUtils.isEmpty(accessTokenStr)) {
                LOGGER.error("Access token is empty.");
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Access token is empty");
                return;
            }

            OAuth2AccessToken accessToken = jwtTokenStore.readAccessToken(accessTokenStr);
            if (accessToken == null || accessToken.isExpired()) {
                LOGGER.error("Access token has expired.");
                response.sendError(HttpStatus.UNAUTHORIZED.value(), INVALID_TOKEN_MESSAGE);
                return;
            }

            Map<String, Object> additionalInfoMap = accessToken.getAdditionalInformation();
            OAuth2Authentication auth = jwtTokenStore.readAuthentication(accessToken);
            if (additionalInfoMap == null || auth == null) {
                LOGGER.error("Access token is invalid.");
                response.sendError(HttpStatus.UNAUTHORIZED.value(), INVALID_TOKEN_MESSAGE);
                return;
            }

            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }
}
