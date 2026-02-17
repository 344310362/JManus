/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.lynxe.codeagent.config;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds Cross-Origin-Embedder-Policy and Cross-Origin-Opener-Policy headers required by
 * the WebContainer API.
 */
@Configuration
public class CrossOriginIsolationConfig {

	@Bean
	public FilterRegistrationBean<Filter> crossOriginIsolationFilter() {
		FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
		registration.setFilter(new CrossOriginIsolationFilter());
		registration.addUrlPatterns("/ui/*");
		registration.setOrder(0);
		return registration;
	}

	static class CrossOriginIsolationFilter implements Filter {

		@Override
		public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
				throws IOException, ServletException {
			if (response instanceof HttpServletResponse httpResponse) {
				httpResponse.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
				httpResponse.setHeader("Cross-Origin-Opener-Policy", "same-origin");
			}
			chain.doFilter(request, response);
		}

	}

}
