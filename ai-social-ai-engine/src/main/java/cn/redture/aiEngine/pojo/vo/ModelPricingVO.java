package cn.redture.aiEngine.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelPricingVO {
    @JsonProperty("input_price_per_million")
    private BigDecimal inputPricePerMillion;
    
    @JsonProperty("output_price_per_million")
    private BigDecimal outputPricePerMillion;
}
