package me.zhengziheng.agent.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "RAG 知识库问答 API",
                version = "1.0",
                description = "基于 PGVector 的检索增强生成问答系统接口文档"
        )
)
public class OpenApiConfig {

    @Bean
    public OpenAPI ragKbOpenAPI() {
        return new OpenAPI()
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("本地开发"),
                        new Server().url("https://your-domain.com").description("生产")
                ));
    }
}
