package me.zhengziheng.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 个人 AI 分身站 · 后端启动类。
 * 技术栈：Spring Boot 3 + MyBatis-Plus + PGVector（PostgreSQL 向量扩展）。
 * @MapperScan 让 MyBatis 扫描 me.zhengziheng.agent.mapper 下的接口并自动生成实现。
 */
@SpringBootApplication
@MapperScan("me.zhengziheng.agent.mapper")
public class PersonalAgentApplication {

    public static void main(String[] args) {
        // 启动 Spring 容器；Flyway 会在此期间自动连库并执行 V1__init_schema.sql 建表
        SpringApplication.run(PersonalAgentApplication.class, args);
    }
}
