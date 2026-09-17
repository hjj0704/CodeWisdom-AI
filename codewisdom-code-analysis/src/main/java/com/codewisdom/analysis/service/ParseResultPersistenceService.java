package com.codewisdom.analysis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codewisdom.analysis.entity.ParseResultEntity;
import com.codewisdom.analysis.mapper.ParseResultMapper;
import com.codewisdom.analysis.parser.SourceStructure;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class ParseResultPersistenceService {

    private final ParseResultMapper parseResultMapper;
    private final ObjectMapper objectMapper;

    public ParseResultPersistenceService(ParseResultMapper parseResultMapper, ObjectMapper objectMapper) {
        this.parseResultMapper = parseResultMapper;
        this.objectMapper = objectMapper;
    }

    public void upsert(long projectId, String filePath, String checksum, SourceStructure structure) {
        String json = toJson(structure);
        ParseResultEntity existing = parseResultMapper.selectOne(new LambdaQueryWrapper<ParseResultEntity>()
                .eq(ParseResultEntity::getProjectId, projectId)
                .eq(ParseResultEntity::getFilePath, filePath));

        if (existing == null) {
            ParseResultEntity entity = new ParseResultEntity();
            entity.setProjectId(projectId);
            entity.setFilePath(filePath);
            entity.setChecksum(checksum);
            entity.setTypeCount(structure.types().size());
            entity.setMethodCount(structure.methods().size());
            entity.setStructureJson(json);
            parseResultMapper.insert(entity);
            return;
        }

        existing.setChecksum(checksum);
        existing.setTypeCount(structure.types().size());
        existing.setMethodCount(structure.methods().size());
        existing.setStructureJson(json);
        parseResultMapper.updateById(existing);
    }

    private String toJson(SourceStructure structure) {
        try {
            return objectMapper.writeValueAsString(structure);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("解析结果 JSON 序列化失败", ex);
        }
    }
}
