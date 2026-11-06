package vip.mate.wiki.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 病历模板库实体（方案 B：独立维护，可选关联某个 Wiki 知识库 kbId）。
 * <p>
 * 设计要点——"样式"与"内容"绑定且可分离：
 * <ul>
 *   <li>{@code styleSignature} / {@code styleEmbedding} / {@code styleHash} 描述模板<b>样式</b>
 *       （布局、字段骨架、表格结构），用于"召回样式类似的模板"；</li>
 *   <li>{@code contentJson} 是模板对应的<b>内容 JSON</b>，手动录入，与样式同表同行 → 天然绑定；</li>
 *   <li>{@code extAttrsJson} 是<b>扩展属性 JSON</b>，手动录入（如适用科室、版本、标签等）；</li>
 *   <li>{@code styleHash} 唯一约束保证"一个样式只存一份"，contentJson 不会张冠李戴。</li>
 * </ul>
 */
@Data
@TableName("mate_wiki_record_template")
public class WikiRecordTemplateEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 模板名称（手动录入；文件上传时取文件名） */
    private String name;

    /** 来源格式：docx / xlsx / pdf / json（手动录入无文件时为 json） */
    private String sourceType;

    /** 原始文件名 */
    private String fileName;

    /** 服务器暂存的原始文件路径 */
    private String filePath;

    /** 所属工作区（默认 1） */
    private Long workspaceId;
    /**
     * 知识库ID
     */
    private Long kbId;

    /** 状态：draft（仅抽取了样式，待补内容）/ published（已填内容） */
    private String status;

    /** 样式签名（规范化结构 JSON） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String styleSignature;

    /** 归一化样式指纹（SHA-256），用于精确去重/精确匹配 */
    private String styleHash;

    /** 样式向量（float32 little-endian，复用 WikiChunkEntity.embedding 约定） */
    private byte[] styleEmbedding;

    /** 生成 styleEmbedding 的模型名 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String embeddingModel;

    /** 生成 styleEmbedding 的输入版本 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String embeddingTextVersion;

    /** 布局类型：outline / form_with_tables / grid_sheet / mixed / unknown */
    private String layoutType;

    /** 字段数（索引/粗筛用） */
    private Integer fieldCount;

    /** 表格数 */
    private Integer tableCount;

    /** 章节数 */
    private Integer sectionCount;

    /** 模板内容 JSON（手动录入，与样式绑定） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contentJson;

    /** 扩展属性 JSON（手动录入） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String extAttrsJson;

    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
