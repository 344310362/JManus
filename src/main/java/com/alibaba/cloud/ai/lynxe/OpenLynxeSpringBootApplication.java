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

package com.alibaba.cloud.ai.lynxe;

import com.microsoft.playwright.Playwright;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;

@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories(basePackages = { "com.alibaba.cloud.ai.lynxe" })
@EntityScan(basePackages = { "com.alibaba.cloud.ai.lynxe" })
@EnableFeignClients(basePackages = {"cn.iocoder.cloud.devops.api"})
@ComponentScan(basePackages = { "com.alibaba.cloud.ai.lynxe","cn.iocoder.cloud.devops.api" })
public class OpenLynxeSpringBootApplication {

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args != null && args.length >= 1 && args[0].equals("playwright-init")) {
			Playwright.create();
			System.out.println("Playwright init finished");
			System.exit(0);
		}
		else {
			SpringApplication.run(OpenLynxeSpringBootApplication.class, args);
		}
	}

	@Bean
	public ApplicationRunner checkFeignClients(ApplicationContext ctx) {
		return args -> {
			System.out.println("===== Registered Feign Clients =====");
			String[] feignBeans = ctx.getBeanNamesForAnnotation(org.springframework.cloud.openfeign.FeignClient.class);
			for (String name : feignBeans) {
				System.out.println("Feign Bean: " + name);
			}

			String[] allBeans = ctx.getBeanNamesForType(cn.iocoder.cloud.devops.api.service.ServiceApi.class);
			System.out.println("===== ServiceApi Beans =====");
			for (String name : allBeans) {
				System.out.println("Bean: " + name);
			}
		};
	}


}
