package vip.mate.wiki.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 修改人:祝奔；时间：2026-11-18T09:27:23.022Z；修改原因/内容：新增 Wiki 素材内嵌图片元数据实体，记录落盘文件、归属工作区与来源引用；代码来源：AI生成
 *
 * <p>图片本体落盘在数据目录，正文只保存形如 {@code /wiki-images/{id}.{ext}} 的引用；
 * 访问由 {@code /api/v1/wiki/images/{id}} 鉴权接口提供，前端转成带鉴权的 blob URL 显示。
 */
@Data
@TableName("mate_wiki_image")
public class WikiImageEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属知识库 ID */
    private Long kbId;

    /** 来源原始材料 ID */
    private Long rawId;

    /** 归属工作区（鉴权时校验与请求 header 一致） */
    private Long workspaceId;

    /** 扩展名（不含点），如 png / jpg */
    private String ext;

    /** MIME 类型，如 image/png */
    private String mimeType;

    /** 文件字节数 */
    private Long fileSize;

    /** 素材内原始引用，如 media/image1.png 或 P1#2 */
    private String sourceRef;

    // 修改人:祝奔；时间：2026-11-20T06:14:14.191Z；修改原因/内容：新增图片内容 SHA-256，用于 workspace+kb 范围内去重；代码来源：AI生成
    /** 图片内容 SHA-256 */
    private String contentHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
