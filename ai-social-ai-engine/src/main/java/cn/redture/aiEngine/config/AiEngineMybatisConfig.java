package cn.redture.aiEngine.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("cn.redture.aiEngine.mapper")
public class AiEngineMybatisConfig {

}