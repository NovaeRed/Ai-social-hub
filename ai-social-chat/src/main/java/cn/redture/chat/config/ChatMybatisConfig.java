package cn.redture.chat.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("cn.redture.chat.mapper")
public class ChatMybatisConfig {
}
