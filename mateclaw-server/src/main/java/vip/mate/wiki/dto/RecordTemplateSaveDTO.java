package vip.mate.wiki.dto;

import lombok.Data;

/**
 * 病历模板保存/更新入参（方案 B）。
 * <p>create 时 name / contentJson 必填；update 时各字段均可选。
 */
@Data
public class RecordTemplateSaveDTO {
    /** 模板名称（手动录入时必填；文件上传时取文件名，可不传） */
    private String name;

    /** 模板内容 JSON（手动录入必填） */
    private String contentJson;

    /** 扩展属性 JSON（可选） */
    private String extAttrsJson;

    /** 来源格式：docx / xlsx / pdf / json（手动录入可选，默认 json） */
    private String sourceType;

    /** 关联知识库 ID（对应 mate_wiki_knowledge_base.id） */
    private Long kbId;
}
