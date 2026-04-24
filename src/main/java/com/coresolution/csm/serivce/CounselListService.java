package com.coresolution.csm.serivce;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.csm.mapper.CsmListMapper;
import com.coresolution.csm.mapper.CsmMapper;
import com.coresolution.csm.vo.OrderedItem;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CounselListService {

    @Autowired
    private CsmListMapper mapper;

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
}
