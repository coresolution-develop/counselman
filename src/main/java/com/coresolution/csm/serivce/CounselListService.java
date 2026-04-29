package com.coresolution.csm.serivce;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.csm.mapper.CsmListMapper;
import com.coresolution.csm.vo.OrderedItem;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CounselListService {

    @Autowired
    private CsmListMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Logger logger = LoggerFactory.getLogger(CounselListService.class);

    @Transactional
    public void replaceAll(String inst, List<OrderedItem> items) {
        mapper.deleteAll(inst);
        if (items != null && !items.isEmpty()) {
            mapper.insertBatch(inst, items);
        }
    }

    public String getLogSettings(String inst) {
        try { return mapper.selectLogSettings(inst); } catch (Exception e) { return null; }
    }

    @Transactional
    public void saveLogSettings(String inst, String json) {
        ensureCommentColumnText(inst);
        mapper.deleteLogSettings(inst);
        mapper.insertLogSettings(inst, json);
    }

    // (기존 컨트롤러 루프를 그대로 쓰고 싶다면)
    @Transactional
    public void replaceAllLoop(String inst, List<OrderedItem> items) {
        mapper.deleteAll(inst);
        if (items != null) {
            for (OrderedItem it : items) {
                mapper.insertOne(inst, it);
            }
        }
    }

    private void ensureCommentColumnText(String inst) {
        String safe = inst.replaceAll("[^a-zA-Z0-9_]", "_");
        try {
            Integer maxLen = jdbcTemplate.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = 'csm' AND TABLE_NAME = ? AND COLUMN_NAME = 'comment' AND DATA_TYPE = 'varchar'",
                Integer.class, "counsel_list_" + safe);
            if (maxLen != null) {
                jdbcTemplate.execute("ALTER TABLE csm.counsel_list_" + safe + " MODIFY COLUMN comment TEXT");
            }
        } catch (Exception e) {
            logger.warn("[CounselListService] comment column migration skipped: {}", e.getMessage());
        }
    }
}
