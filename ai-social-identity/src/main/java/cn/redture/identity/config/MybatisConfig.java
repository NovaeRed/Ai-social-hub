package cn.redture.identity.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("cn.redture.identity.mapper")
public class MybatisConfig {

}