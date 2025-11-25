package com.alibaba.cloud.ai.manus.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ServiceHelper implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        context = ctx;
        log.info("Injected ApplicationContext = {}", ctx.hashCode());
    }

    /**
     * 通用获取 Bean 方法
     */
    public static <T> T getBean(Class<T> clazz) {
        if (context == null) {
            log.warn("Spring ApplicationContext is not initialized yet.");
            return null;
        }
        try {
            return context.getBean(clazz);
        } catch (Exception e) {
            log.warn("Failed to get bean of type {}: {}", clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }

    public static <T> T getFeignBean(Class<T> clazz) {
        try {
            String[] names = context.getBeanNamesForType(clazz);
            if (names.length == 0) {
                log.warn("No bean found for type {}", clazz.getName());
                return null;
            }

            for (String name : names) {
                try {
                    // 优先尝试直接取
                    return context.getBean(name, clazz);
                } catch (Exception ex) {
                    // 若是 FeignClient FactoryBean 异常，改用 &name 拿 FactoryBean 本身
                    Object factory = context.getBean("&" + name);
                    if (factory instanceof FactoryBean<?>) {
                        Object feign = ((FactoryBean<?>) factory).getObject();
                        return clazz.cast(feign);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("getBeanSafe failed for {}: {}", clazz.getSimpleName(), e.getMessage());
        }
        return null;
    }

    /**
     * 获取当前容器
     */
    public static ApplicationContext getContext() {
        return context;
    }
}
