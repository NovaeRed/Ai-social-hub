package cn.redture.schedule.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("cn.redture.schedule.mapper")
public class ScheduleMybatisConfig {
}
