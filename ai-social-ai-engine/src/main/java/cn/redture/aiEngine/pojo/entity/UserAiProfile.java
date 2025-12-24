package cn.redture.aiEngine.pojo.entity;

import cn.redture.aiEngine.handler.JsonbTypeHandler;
import cn.redture.aiEngine.handler.PGvectorTypeHandler;
import cn.redture.aiEngine.pojo.vo.PersonaAnalysisResultVO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户AI画像实体
 */
@Data
@TableName(value = "user_ai_profiles", autoResultMap = true)
public class UserAiProfile {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    /**
     * 画像类型：PERSONA, PREFERENCES, BEHAVIOR等
     */
    private String profileType;
    
    /**
     * 生成此画像的模型名称
     */
    private String modelName;
    
    /**
     * 模型版本
     */
    private String modelVersion;
    
    /**
     * AI服务提供商
     */
    private String provider;
    
    /**
     * 结构化内容（JSON）
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private PersonaAnalysisResultVO content;
    
    /**
     * 向量表示
     */
    @TableField(value = "embedding", typeHandler = PGvectorTypeHandler.class)
    private List<Double> embedding;
    
    /**
     * 版本号
     */
    private Integer version;
    
    private OffsetDateTime createdAt;
    
    private OffsetDateTime updatedAt;
}
