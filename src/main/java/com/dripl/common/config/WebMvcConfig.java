package com.dripl.common.config;

import com.dripl.common.resolver.SubjectArgumentResolver;
import com.dripl.common.resolver.UserIdArgumentResolver;
import com.dripl.common.resolver.WorkspaceIdArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new WorkspaceIdArgumentResolver());
        resolvers.add(new UserIdArgumentResolver());
        resolvers.add(new SubjectArgumentResolver());
    }
}
