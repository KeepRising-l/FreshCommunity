package com.freshcommunity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "商品名称不能为空")
    private String name;

    /** 分类（自由文本），为空时由服务端落默认「其他」 */
    private String category;

    @NotNull(message = "价格不能为空")
    private BigDecimal price;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能小于0")
    private Integer stock;

    private String imageUrl;

    @Size(max = 500, message = "商品描述不能超过 500 字")
    private String description;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    /** 评分汇总，非数据库字段，仅详情接口填充 */
    @TableField(exist = false)
    private Double avgScore;
    @TableField(exist = false)
    private Long reviewCount;
    /** 口碑标签（来自评价洞察），非数据库字段，仅详情接口填充 */
    @TableField(exist = false)
    private java.util.List<String> insightTags;
    /** 图集（多图），非数据库字段，仅详情接口填充；首图即 imageUrl */
    @TableField(exist = false)
    private java.util.List<String> images;
}
